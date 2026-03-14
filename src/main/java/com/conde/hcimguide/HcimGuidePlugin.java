package com.conde.hcimguide;

import com.conde.hcimguide.model.GuideData;
import com.conde.hcimguide.model.GuideSection;
import com.conde.hcimguide.model.GuideStep;
import com.conde.hcimguide.service.GuideAutoProgressService;
import com.conde.hcimguide.service.GuideProgressStore;
import com.conde.hcimguide.service.GuideRepository;
import com.conde.hcimguide.ui.GuidePanel;
import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.LinkBrowser;

@PluginDescriptor(
	name = "HCIM Guide",
	description = "Sidebar guide for B0aty HCIM Guide V3",
	tags = {"hcim", "ironman", "guide", "quest"}
)
public class HcimGuidePlugin extends Plugin
{
	private static final String GUIDE_URL = "https://oldschool.runescape.wiki/w/Guide:B0aty_HCIM_Guide_V3";

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private HcimGuideConfig config;

	@Inject
	private Client client;

	@Inject
	private GuideRepository guideRepository;

	private GuideData guideData;
	private GuidePanel panel;
	private NavigationButton navigationButton;
	private GuideProgressStore progressStore;
	private final GuideAutoProgressService autoProgressService = new GuideAutoProgressService();
	private final Set<String> completedStepIds = new LinkedHashSet<>();
	private String autoProgressText = "Auto: manual";
	private String lastAutoSatisfiedStepId;

	@Provides
	HcimGuideConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HcimGuideConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		guideData = guideRepository.load();
		progressStore = new GuideProgressStore(configManager);
		completedStepIds.clear();
		completedStepIds.addAll(progressStore.getCompletedStepIds());

		panel = new GuidePanel(this);
		navigationButton = NavigationButton.builder()
			.tooltip("HCIM Guide")
			.icon(createIcon())
			.priority(5)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		refreshState();
	}

	@Override
	protected void shutDown()
	{
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}

		panel = null;
		guideData = null;
		progressStore = null;
		completedStepIds.clear();
		autoProgressText = "Auto: manual";
		lastAutoSatisfiedStepId = null;
	}

	public List<GuideSection> getSections()
	{
		return guideData == null ? Collections.emptyList() : guideData.getSections();
	}

	public String getGuideTitle()
	{
		return guideData == null ? "HCIM Guide" : guideData.getTitle();
	}

	public int getCurrentSectionIndex()
	{
		return clampSectionIndex(progressStore == null ? 0 : progressStore.getCurrentSectionIndex());
	}

	public GuideSection getCurrentSection()
	{
		List<GuideSection> sections = getSections();
		if (sections.isEmpty())
		{
			return null;
		}

		return sections.get(getCurrentSectionIndex());
	}

	public GuideStep getCurrentStep()
	{
		GuideSection section = getCurrentSection();
		if (section == null || section.getSteps().isEmpty())
		{
			return null;
		}

		int stepIndex = clampStepIndex(section, progressStore == null ? 0 : progressStore.getCurrentStepIndex());
		return section.getSteps().get(stepIndex);
	}

	public String getCurrentStepText()
	{
		GuideStep step = getCurrentStep();
		if (step == null)
		{
			return "No step loaded.";
		}

		return step.getIndex() + ". " + step.getText();
	}

	public String getProgressText()
	{
		GuideSection section = getCurrentSection();
		if (section == null)
		{
			return "No guide data loaded.";
		}

		GuideStep step = getCurrentStep();
		int stepIndex = step == null ? 0 : step.getIndex();
		return "Section " + (getCurrentSectionIndex() + 1) + "/" + getSections().size()
			+ "  Step " + stepIndex + "/" + section.getSteps().size();
	}

	public String getAutoProgressText()
	{
		if (!config.showAutoStatus())
		{
			return "";
		}

		return autoProgressText;
	}

	public List<GuideStep> getVisibleSteps()
	{
		GuideSection section = getCurrentSection();
		if (section == null)
		{
			return Collections.emptyList();
		}

		if (config.showCompletedSteps())
		{
			return section.getSteps();
		}

		return section.getSteps().stream()
			.filter(step -> !completedStepIds.contains(step.getId()))
			.collect(java.util.stream.Collectors.toList());
	}

	public Set<String> getCompletedStepIds()
	{
		return Collections.unmodifiableSet(completedStepIds);
	}

	public void goToSection(int sectionIndex)
	{
		if (progressStore == null)
		{
			return;
		}

		int targetSectionIndex = clampSectionIndex(sectionIndex);
		if (targetSectionIndex == getCurrentSectionIndex())
		{
			return;
		}

		progressStore.setCurrentSectionIndex(targetSectionIndex);
		progressStore.setCurrentStepIndex(0);
		refreshState();
	}

	public void goToStep(int stepIndex)
	{
		GuideSection section = getCurrentSection();
		if (section == null || progressStore == null)
		{
			return;
		}

		int targetStepIndex = clampStepIndex(section, stepIndex);
		if (targetStepIndex == progressStore.getCurrentStepIndex())
		{
			return;
		}

		progressStore.setCurrentStepIndex(targetStepIndex);
		refreshState();
	}

	public void previousStep()
	{
		GuideSection section = getCurrentSection();
		if (section == null || progressStore == null)
		{
			return;
		}

		int stepIndex = clampStepIndex(section, progressStore.getCurrentStepIndex());
		if (stepIndex > 0)
		{
			progressStore.setCurrentStepIndex(stepIndex - 1);
		}
		else if (getCurrentSectionIndex() > 0)
		{
			int previousSectionIndex = getCurrentSectionIndex() - 1;
			progressStore.setCurrentSectionIndex(previousSectionIndex);
			GuideSection previousSection = getSections().get(previousSectionIndex);
			progressStore.setCurrentStepIndex(Math.max(0, previousSection.getSteps().size() - 1));
		}

		refreshState();
	}

	public void nextStep()
	{
		GuideSection section = getCurrentSection();
		if (section == null || progressStore == null)
		{
			return;
		}

		int stepIndex = clampStepIndex(section, progressStore.getCurrentStepIndex());
		if (stepIndex < section.getSteps().size() - 1)
		{
			progressStore.setCurrentStepIndex(stepIndex + 1);
		}
		else if (getCurrentSectionIndex() < getSections().size() - 1)
		{
			progressStore.setCurrentSectionIndex(getCurrentSectionIndex() + 1);
			progressStore.setCurrentStepIndex(0);
		}

		refreshState();
	}

	public void toggleCurrentStepComplete()
	{
		GuideStep step = getCurrentStep();
		if (step == null || progressStore == null)
		{
			return;
		}

		if (completedStepIds.contains(step.getId()))
		{
			completedStepIds.remove(step.getId());
			progressStore.setCompletedStepIds(completedStepIds);
			refreshState();
			return;
		}

		setCurrentStepCompleted(true, true);
	}

	public void openWikiPage()
	{
		LinkBrowser.browse(GUIDE_URL);
	}

	public void openCurrentVideo()
	{
		GuideSection section = getCurrentSection();
		if (section == null || section.getYoutubeId() == null || section.getYoutubeId().trim().isEmpty())
		{
			openWikiPage();
			return;
		}

		LinkBrowser.browse("https://www.youtube.com/watch?v=" + section.getYoutubeId());
	}

	public void resetProgress()
	{
		if (progressStore == null)
		{
			return;
		}

		progressStore.reset();
		completedStepIds.clear();
		lastAutoSatisfiedStepId = null;
		refreshState();
	}

	public void refreshPanel()
	{
		if (panel != null)
		{
			panel.refresh();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (updateAutoProgress(true))
		{
			refreshPanel();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if ("hcimguide".equals(event.getGroup()))
		{
			refreshState();
		}
	}

	private void refreshState()
	{
		updateAutoProgress(false);
		refreshPanel();
	}

	private boolean updateAutoProgress(boolean allowCompletion)
	{
		String nextStatus = "Auto: manual";

		if (!config.enableAutoProgress())
		{
			if (allowCompletion)
			{
				lastAutoSatisfiedStepId = null;
			}
			return setAutoProgressText("Auto: disabled");
		}

		if (client == null || client.getGameState() != GameState.LOGGED_IN)
		{
			if (allowCompletion)
			{
				lastAutoSatisfiedStepId = null;
			}
			return setAutoProgressText("Auto: waiting for login");
		}

		GuideStep step = getCurrentStep();
		Optional<GuideAutoProgressService.Evaluation> evaluation = autoProgressService.evaluate(client, step);
		if (!evaluation.isPresent())
		{
			if (allowCompletion)
			{
				lastAutoSatisfiedStepId = null;
			}
			return setAutoProgressText(nextStatus);
		}

		GuideAutoProgressService.Evaluation match = evaluation.get();
		nextStatus = match.isSatisfied()
			? "Auto: ready - " + match.getDescription()
			: "Auto: tracking - " + match.getDescription();

		if (allowCompletion)
		{
			if (!match.isSatisfied() || step == null)
			{
				lastAutoSatisfiedStepId = null;
			}
			else if (step.getId().equals(lastAutoSatisfiedStepId))
			{
				// Prevent immediate re-completion after a manual uncheck on an already satisfied step.
			}
			else if (!completedStepIds.contains(step.getId()))
			{
				lastAutoSatisfiedStepId = step.getId();
				setAutoProgressText(nextStatus);
				setCurrentStepCompleted(true, config.advanceWhenAutoCompleted());
				return false;
			}
			else
			{
				lastAutoSatisfiedStepId = step.getId();
			}
		}

		return setAutoProgressText(nextStatus);
	}

	private boolean setAutoProgressText(String nextStatus)
	{
		if (Objects.equals(autoProgressText, nextStatus))
		{
			return false;
		}

		autoProgressText = nextStatus;
		return true;
	}

	private void setCurrentStepCompleted(boolean completed, boolean advance)
	{
		GuideStep step = getCurrentStep();
		if (step == null || progressStore == null)
		{
			return;
		}

		boolean changed = completed ? completedStepIds.add(step.getId()) : completedStepIds.remove(step.getId());
		if (!changed)
		{
			refreshState();
			return;
		}

		progressStore.setCompletedStepIds(completedStepIds);
		if (completed && advance)
		{
			nextStep();
			return;
		}

		refreshState();
	}

	private int clampSectionIndex(int sectionIndex)
	{
		List<GuideSection> sections = getSections();
		if (sections.isEmpty())
		{
			return 0;
		}

		return Math.max(0, Math.min(sectionIndex, sections.size() - 1));
	}

	private int clampStepIndex(GuideSection section, int stepIndex)
	{
		if (section.getSteps().isEmpty())
		{
			return 0;
		}

		return Math.max(0, Math.min(stepIndex, section.getSteps().size() - 1));
	}

	private BufferedImage createIcon()
	{
		BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setColor(new Color(31, 22, 15));
			graphics.fillRoundRect(2, 2, 28, 28, 8, 8);
			graphics.setColor(new Color(213, 176, 93));
			graphics.setStroke(new BasicStroke(3F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			graphics.drawRoundRect(3, 3, 26, 26, 8, 8);
			graphics.drawLine(11, 9, 11, 23);
			graphics.drawLine(21, 9, 21, 23);
			graphics.drawLine(11, 16, 21, 16);
		}
		finally
		{
			graphics.dispose();
		}

		return image;
	}
}
