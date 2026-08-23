package com.conde.hcimguide;

import com.conde.hcimguide.model.GuideData;
import com.conde.hcimguide.model.GuideSection;
import com.conde.hcimguide.model.GuideStep;
import com.conde.hcimguide.model.StepMetadata;
import com.conde.hcimguide.service.GuideAutoProgressService;
import com.conde.hcimguide.service.GuideProgressStore;
import com.conde.hcimguide.service.GuideRepository;
import com.conde.hcimguide.service.StepMetadataRepository;
import com.conde.hcimguide.service.WithdrawService;
import com.conde.hcimguide.model.WithdrawItem;
import com.conde.hcimguide.model.WithdrawLine;
import com.conde.hcimguide.ui.CurrentStepOverlay;
import com.conde.hcimguide.ui.WithdrawBankFilter;
import com.conde.hcimguide.ui.WithdrawBankOverlay;
import com.conde.hcimguide.ui.WithdrawOverlay;
import com.conde.hcimguide.ui.GuidePanel;
import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.LinkBrowser;

@PluginDescriptor(
	name = "HCIM Guide",
	description = "Sidebar guide for B0aty HCIM Guide V3",
	tags = {"hcim", "ironman", "guide", "quest"}
)
public class HcimGuidePlugin extends Plugin
{
	private static final String GUIDE_URL = "https://oldschool.runescape.wiki/w/Guide:B0aty_HCIM_Guide_V3";
	private static final String SHORTEST_PATH_NAMESPACE = "shortestpath";

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private HcimGuideConfig config;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private EventBus eventBus;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private GuideRepository guideRepository;

	@Inject
	private StepMetadataRepository stepMetadataRepository;

	@Inject
	private CurrentStepOverlay currentStepOverlay;

	@Inject
	private WithdrawOverlay withdrawOverlay;

	@Inject
	private WithdrawBankOverlay withdrawBankOverlay;

	@Inject
	private WithdrawBankFilter withdrawBankFilter;

	@Inject
	private WithdrawService withdrawService;

	private GuideData guideData;
	private GuidePanel panel;
	private NavigationButton navigationButton;
	private GuideProgressStore progressStore;
	private final GuideAutoProgressService autoProgressService = new GuideAutoProgressService();
	private final Set<String> completedStepIds = ConcurrentHashMap.newKeySet();
	private volatile String autoProgressText = "Auto: manual";
	private volatile String lastAutoSatisfiedStepId;
	private volatile String lastNavStepId;
	private volatile List<WithdrawLine> currentWithdrawLines = Collections.emptyList();

	@Provides
	HcimGuideConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HcimGuideConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		guideData = guideRepository.load();
		stepMetadataRepository.load();
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
		overlayManager.add(currentStepOverlay);
		overlayManager.add(withdrawOverlay);
		overlayManager.add(withdrawBankOverlay);
		refreshState();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(currentStepOverlay);
		overlayManager.remove(withdrawOverlay);
		overlayManager.remove(withdrawBankOverlay);
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}

		if (lastNavStepId != null)
		{
			lastNavStepId = null;
			eventBus.post(new PluginMessage(SHORTEST_PATH_NAMESPACE, "clear"));
		}

		withdrawBankFilter.reset();
		panel = null;
		guideData = null;
		progressStore = null;
		completedStepIds.clear();
		autoProgressText = "Auto: manual";
		lastAutoSatisfiedStepId = null;
	}

	public List<GuideSection> getSections()
	{
		GuideData data = guideData;
		return data == null ? Collections.emptyList() : data.getSections();
	}

	public String getGuideTitle()
	{
		GuideData data = guideData;
		return data == null ? "HCIM Guide" : data.getTitle();
	}

	public int getCurrentSectionIndex()
	{
		GuideProgressStore store = progressStore;
		return clampSectionIndex(store == null ? 0 : store.getCurrentSectionIndex());
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

		GuideProgressStore store = progressStore;
		int stepIndex = clampStepIndex(section, store == null ? 0 : store.getCurrentStepIndex());
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

	public StepMetadata.NavTarget getCurrentNavTarget()
	{
		GuideStep step = getCurrentStep();
		if (step == null)
		{
			return null;
		}

		StepMetadata metadata = stepMetadataRepository.get(step.getId());
		return metadata == null ? null : nearestTarget(metadata);
	}

	/**
	 * The step's destination, or for a multi-destination step the one closest to the
	 * player so a "collect A and B" step routes to whichever is nearer. Falls back to
	 * the first target when the player's position is unknown.
	 *
	 * <p>Must be called on the client thread — it reads the local player.
	 */
	private StepMetadata.NavTarget nearestTarget(StepMetadata metadata)
	{
		List<StepMetadata.NavTarget> targets = metadata.getNavTargets();
		if (targets.size() <= 1)
		{
			return metadata.getNav();
		}

		Player local = client.getLocalPlayer();
		WorldPoint from = local == null ? null : local.getWorldLocation();
		if (from == null)
		{
			return targets.get(0);
		}

		StepMetadata.NavTarget closest = null;
		long best = Long.MAX_VALUE;
		for (StepMetadata.NavTarget target : targets)
		{
			// Plain 2D distance: WorldPoint.distanceTo returns MAX_VALUE across planes,
			// which would hide an upstairs destination that is otherwise the nearest.
			long dx = (long) target.getX() - from.getX();
			long dy = (long) target.getY() - from.getY();
			long distance = dx * dx + dy * dy;
			if (distance < best)
			{
				best = distance;
				closest = target;
			}
		}
		return closest;
	}

	/** Items the current step wants out of the bank; empty if it is not a withdraw step. */
	public List<WithdrawItem> getCurrentWithdrawItems()
	{
		GuideStep step = getCurrentStep();
		if (step == null)
		{
			return Collections.emptyList();
		}
		StepMetadata metadata = stepMetadataRepository.get(step.getId());
		return metadata == null ? Collections.emptyList() : metadata.getWithdraw();
	}

	/** The current step's withdraw items as colour-coded sidebar lines (cached). */
	public List<WithdrawLine> getCurrentWithdrawLines()
	{
		return currentWithdrawLines;
	}

	/**
	 * Recompute the colour-coded withdraw lines. Must run on the client thread — it
	 * reads item containers. Returns whether the lines changed (so a panel refresh is
	 * only queued when something actually moved).
	 */
	private boolean updateWithdrawLines()
	{
		List<WithdrawLine> lines;
		if (!config.showWithdrawItems())
		{
			lines = Collections.emptyList();
		}
		else
		{
			List<WithdrawItem> items = getCurrentWithdrawItems();
			lines = new ArrayList<>(items.size());
			for (WithdrawItem item : items)
			{
				lines.add(new WithdrawLine(withdrawService.resolvedLabel(item), withdrawService.stateOf(item)));
			}
		}
		if (lines.equals(currentWithdrawLines))
		{
			return false;
		}
		currentWithdrawLines = lines;
		return true;
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
		GuideProgressStore store = progressStore;
		if (store == null)
		{
			return;
		}

		int targetSectionIndex = clampSectionIndex(sectionIndex);
		if (targetSectionIndex == getCurrentSectionIndex())
		{
			return;
		}

		store.setCurrentSectionIndex(targetSectionIndex);
		store.setCurrentStepIndex(0);
		refreshState();
	}

	public void goToStep(int stepIndex)
	{
		GuideSection section = getCurrentSection();
		GuideProgressStore store = progressStore;
		if (section == null || store == null)
		{
			return;
		}

		int targetStepIndex = clampStepIndex(section, stepIndex);
		if (targetStepIndex == store.getCurrentStepIndex())
		{
			return;
		}

		store.setCurrentStepIndex(targetStepIndex);
		refreshState();
	}

	public void previousStep()
	{
		GuideSection section = getCurrentSection();
		GuideProgressStore store = progressStore;
		if (section == null || store == null)
		{
			return;
		}

		int stepIndex = clampStepIndex(section, store.getCurrentStepIndex());
		if (stepIndex > 0)
		{
			store.setCurrentStepIndex(stepIndex - 1);
		}
		else if (getCurrentSectionIndex() > 0)
		{
			int previousSectionIndex = getCurrentSectionIndex() - 1;
			store.setCurrentSectionIndex(previousSectionIndex);
			GuideSection previousSection = getSections().get(previousSectionIndex);
			store.setCurrentStepIndex(Math.max(0, previousSection.getSteps().size() - 1));
		}

		refreshState();
	}

	public void nextStep()
	{
		GuideSection section = getCurrentSection();
		GuideProgressStore store = progressStore;
		if (section == null || store == null)
		{
			return;
		}

		int stepIndex = clampStepIndex(section, store.getCurrentStepIndex());
		if (stepIndex < section.getSteps().size() - 1)
		{
			store.setCurrentStepIndex(stepIndex + 1);
		}
		else if (getCurrentSectionIndex() < getSections().size() - 1)
		{
			store.setCurrentSectionIndex(getCurrentSectionIndex() + 1);
			store.setCurrentStepIndex(0);
		}

		refreshState();
	}

	public void toggleCurrentStepComplete()
	{
		GuideStep step = getCurrentStep();
		GuideProgressStore store = progressStore;
		if (step == null || store == null)
		{
			return;
		}

		if (completedStepIds.contains(step.getId()))
		{
			completedStepIds.remove(step.getId());
			store.setCompletedStepIds(completedStepIds);
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
		GuideProgressStore store = progressStore;
		if (store == null)
		{
			return;
		}

		store.reset();
		completedStepIds.clear();
		lastAutoSatisfiedStepId = null;
		refreshState();
	}

	/**
	 * Safe to call from any thread; the actual Swing update always runs on the EDT.
	 */
	public void refreshPanel()
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			GuidePanel currentPanel = panel;
			if (currentPanel != null)
			{
				currentPanel.refresh();
			}
			return;
		}

		SwingUtilities.invokeLater(() ->
		{
			GuidePanel currentPanel = panel;
			if (currentPanel != null)
			{
				currentPanel.refresh();
			}
		});
	}

	/**
	 * The bank rebuilds its item widgets on open, scroll, search and tab change,
	 * discarding anything we changed. Re-apply once it has finished.
	 */
	/**
	 * A fresh bank interface means any widget we added to the previous one is gone.
	 * Creating our button on every build instead would leak two widgets per bank
	 * open, scroll and search, which is enough to stall the client.
	 */
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			withdrawBankFilter.onBankInterfaceLoaded();
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			withdrawBankFilter.onBankInterfaceLoaded();
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING)
		{
			withdrawBankFilter.onBankBuilt();
		}
		else if (event.getScriptId() == ScriptID.BANKMAIN_SEARCH_TOGGLE)
		{
			// Search button or ctrl-f both run this; drop the filter so the search
			// sees the whole bank.
			withdrawBankFilter.onBankSearch();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		boolean changed = updateAutoProgress(true);
		updateShortestPathTarget();
		if (updateWithdrawLines() || changed)
		{
			refreshPanel();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.BANK)
		{
			withdrawService.snapshotBank();
		}
		if ((id == InventoryID.INV || id == InventoryID.WORN || id == InventoryID.BANK)
			&& updateWithdrawLines())
		{
			refreshPanel();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"hcimguide".equals(event.getGroup()) || GuideProgressStore.isProgressKey(event.getKey()))
		{
			return;
		}

		refreshState();
	}

	/**
	 * Safe to call from any thread; client API access is deferred to the client thread.
	 */
	private void refreshState()
	{
		clientThread.invokeLater(() ->
		{
			if (guideData == null)
			{
				// Plugin shut down before this queued refresh ran.
				return;
			}

			updateAutoProgress(false);
			updateShortestPathTarget();
			updateWithdrawLines();
			refreshPanel();
		});
	}

	/**
	 * Must run on the client thread: evaluation reads quest states, skills and item containers.
	 */
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

	/**
	 * Sends the current step's destination to the Shortest Path plugin, or clears the
	 * previously requested path when the step no longer has one. Deduplicated per step.
	 */
	private void updateShortestPathTarget()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		StepMetadata.NavTarget nav = null;
		String navStepId = null;
		if (config.enableShortestPath())
		{
			GuideStep step = getCurrentStep();
			if (step != null)
			{
				StepMetadata metadata = stepMetadataRepository.get(step.getId());
				nav = metadata == null ? null : nearestTarget(metadata);
				// Key on the chosen point, not just the step: for a multi-destination
				// step the nearest target changes as the player moves, and that must
				// re-path rather than be swallowed by the dedup below.
				navStepId = nav == null ? null
					: step.getId() + "@" + nav.getX() + "," + nav.getY() + "," + nav.getZ();
			}
		}

		if (Objects.equals(navStepId, lastNavStepId))
		{
			return;
		}

		lastNavStepId = navStepId;
		if (nav == null)
		{
			eventBus.post(new PluginMessage(SHORTEST_PATH_NAMESPACE, "clear"));
			return;
		}

		Map<String, Object> data = new HashMap<>();
		data.put("target", nav.toWorldPoint());
		eventBus.post(new PluginMessage(SHORTEST_PATH_NAMESPACE, "path", data));
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
		GuideProgressStore store = progressStore;
		if (step == null || store == null)
		{
			return;
		}

		boolean changed = completed ? completedStepIds.add(step.getId()) : completedStepIds.remove(step.getId());
		if (!changed)
		{
			refreshState();
			return;
		}

		store.setCompletedStepIds(completedStepIds);
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

	/** The sidebar icon. Static so the bank button can reuse the same artwork. */
	public static BufferedImage createIcon()
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
