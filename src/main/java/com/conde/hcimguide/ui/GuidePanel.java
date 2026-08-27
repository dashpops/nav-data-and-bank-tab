package com.conde.hcimguide.ui;

import com.conde.hcimguide.HcimGuidePlugin;
import com.conde.hcimguide.model.GuideSection;
import com.conde.hcimguide.model.GuideStep;
import com.conde.hcimguide.model.ItemState;
import com.conde.hcimguide.model.StepMetadata;
import com.conde.hcimguide.model.WithdrawLine;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

public class GuidePanel extends PluginPanel
{
	private static final Color SURFACE_COLOR = new Color(43, 47, 54);
	private static final Color SURFACE_ALT_COLOR = new Color(34, 37, 43);
	private static final Color BORDER_COLOR = new Color(72, 78, 88);
	private static final Color ACCENT_COLOR = new Color(224, 169, 72);
	private static final Color ACCENT_SOFT_COLOR = new Color(90, 74, 35);
	private static final Color SUCCESS_COLOR = new Color(91, 176, 118);
	private static final Color DANGER_COLOR = new Color(214, 96, 96);
	private static final Color INFO_COLOR = new Color(85, 150, 212);
	private static final Color WARNING_COLOR = new Color(205, 152, 79);
	private static final Color MUTED_TEXT_COLOR = new Color(170, 176, 186);

	private final HcimGuidePlugin plugin;
	private final JLabel titleLabel = new JLabel();
	private final JLabel subtitleLabel = new JLabel();
	private final JLabel progressPill = createPillLabel();
	private final JLabel completionPill = createPillLabel();
	private final JLabel autoStatusPill = createPillLabel();
	private final JComboBox<String> sectionSelector = new JComboBox<>();
	private final JProgressBar sectionProgressBar = new JProgressBar();
	private final JLabel currentStepBadge = new JLabel();
	private final JTextArea currentStepText = new JTextArea();
	private final JLabel navTargetLabel = new JLabel();
	private final JPanel withdrawPanel = new JPanel();
	private final JButton previousButton;
	private final JButton toggleButton;
	private final JButton nextButton;
	private final JButton wikiButton;
	private final JButton videoButton;
	private final JButton resetButton;
	private final DefaultListModel<GuideStep> listModel = new DefaultListModel<>();
	private final JList<GuideStep> stepList = new JList<>(listModel);
	private boolean syncingSelection;

	public GuidePanel(HcimGuidePlugin plugin)
	{
		super(false);
		this.plugin = plugin;

		previousButton = createActionButton("Back", plugin::previousStep, SURFACE_COLOR, Color.WHITE);
		toggleButton = createActionButton("Done", plugin::toggleCurrentStepComplete, SUCCESS_COLOR, Color.WHITE);
		nextButton = createActionButton("Next", plugin::nextStep, ACCENT_COLOR, Color.BLACK);
		wikiButton = createActionButton("Wiki", plugin::openWikiPage, INFO_COLOR, Color.WHITE);
		videoButton = createActionButton("Video", plugin::openCurrentVideo, INFO_COLOR.darker(), Color.WHITE);
		resetButton = createActionButton("Reset", plugin::resetProgress, SURFACE_ALT_COLOR, Color.WHITE);

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel root = new JPanel();
		root.setOpaque(false);
		root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

		root.add(createHeaderPanel());
		root.add(Box.createRigidArea(new Dimension(0, 8)));
		root.add(createCurrentStepPanel());
		root.add(Box.createRigidArea(new Dimension(0, 8)));
		root.add(createTimelinePanel());

		add(root, BorderLayout.CENTER);

		sectionSelector.addActionListener(event ->
		{
			if (!syncingSelection && sectionSelector.getSelectedIndex() >= 0 && sectionSelector.getSelectedIndex() != plugin.getCurrentSectionIndex())
			{
				plugin.goToSection(sectionSelector.getSelectedIndex());
			}
		});

		stepList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		stepList.setCellRenderer(new StepRenderer(plugin));
		stepList.setFixedCellHeight(-1);
		stepList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		stepList.addListSelectionListener(event ->
		{
			if (!event.getValueIsAdjusting() && !syncingSelection)
			{
				GuideStep selected = stepList.getSelectedValue();
				if (selected != null)
				{
					int targetIndex = selected.getIndex() - 1;
					GuideStep current = plugin.getCurrentStep();
					if (current == null || current.getIndex() - 1 != targetIndex)
					{
						plugin.goToStep(targetIndex);
					}
				}
			}
		});
	}

	public void refresh()
	{
		syncingSelection = true;
		try
		{
			List<GuideSection> sections = plugin.getSections();
			GuideSection currentSection = plugin.getCurrentSection();
			GuideStep currentStep = plugin.getCurrentStep();
			Set<String> completedIds = plugin.getCompletedStepIds();

			titleLabel.setText(plugin.getGuideTitle());
			subtitleLabel.setText(currentSection == null ? "No section loaded" : currentSection.getDisplayTitle());

			refreshSectionSelector(sections);
			refreshSummary(currentSection, currentStep, completedIds);
			refreshCurrentStepCard(currentSection, currentStep, completedIds);
			refreshTimeline(currentStep);
		}
		finally
		{
			syncingSelection = false;
		}
	}

	private JPanel createHeaderPanel()
	{
		JPanel panel = createCardPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titleLabel.setForeground(Color.WHITE);

		subtitleLabel.setFont(FontManager.getRunescapeSmallFont());
		subtitleLabel.setForeground(MUTED_TEXT_COLOR);

		sectionSelector.setMaximumRowCount(20);
		sectionSelector.setFocusable(false);

		sectionProgressBar.setBorderPainted(false);
		sectionProgressBar.setBackground(SURFACE_ALT_COLOR);
		sectionProgressBar.setForeground(ACCENT_COLOR);
		sectionProgressBar.setPreferredSize(new Dimension(0, 10));
		sectionProgressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));

		JPanel titleRow = new JPanel(new BorderLayout(0, 4));
		titleRow.setOpaque(false);
		titleRow.add(titleLabel, BorderLayout.NORTH);
		titleRow.add(subtitleLabel, BorderLayout.SOUTH);

		JPanel pillRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		pillRow.setOpaque(false);
		pillRow.add(progressPill);
		pillRow.add(completionPill);
		pillRow.add(autoStatusPill);

		panel.add(titleRow);
		panel.add(Box.createRigidArea(new Dimension(0, 8)));
		panel.add(sectionSelector);
		panel.add(Box.createRigidArea(new Dimension(0, 8)));
		panel.add(pillRow);
		panel.add(Box.createRigidArea(new Dimension(0, 8)));
		panel.add(createCompactActionPanel());
		panel.add(Box.createRigidArea(new Dimension(0, 8)));
		panel.add(sectionProgressBar);
		return panel;
	}

	private JPanel createCurrentStepPanel()
	{
		JPanel wrapper = createCardPanel();
		wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));

		currentStepBadge.setFont(FontManager.getRunescapeSmallFont());
		currentStepBadge.setOpaque(true);
		currentStepBadge.setBackground(ACCENT_SOFT_COLOR);
		currentStepBadge.setForeground(ACCENT_COLOR);
		currentStepBadge.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORDER_COLOR),
			BorderFactory.createEmptyBorder(4, 8, 4, 8)));
		currentStepBadge.setAlignmentX(Component.LEFT_ALIGNMENT);

		currentStepText.setLineWrap(true);
		currentStepText.setWrapStyleWord(true);
		currentStepText.setEditable(false);
		currentStepText.setOpaque(false);
		currentStepText.setForeground(Color.WHITE);
		currentStepText.setFont(FontManager.getRunescapeSmallFont());
		currentStepText.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		currentStepText.setRows(4);
		currentStepText.setAlignmentX(Component.LEFT_ALIGNMENT);

		navTargetLabel.setFont(FontManager.getRunescapeSmallFont());
		navTargetLabel.setForeground(INFO_COLOR);
		navTargetLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		navTargetLabel.setVisible(false);

		withdrawPanel.setOpaque(false);
		withdrawPanel.setLayout(new BoxLayout(withdrawPanel, BoxLayout.Y_AXIS));
		withdrawPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		withdrawPanel.setVisible(false);

		wrapper.add(currentStepBadge);
		wrapper.add(Box.createRigidArea(new Dimension(0, 8)));
		wrapper.add(currentStepText);
		wrapper.add(navTargetLabel);
		wrapper.add(Box.createRigidArea(new Dimension(0, 6)));
		wrapper.add(withdrawPanel);
		return wrapper;
	}

	private JPanel createCompactActionPanel()
	{
		JPanel panel = new JPanel(new DynamicGridLayout(2, 3, 6, 6));
		panel.setOpaque(false);
		panel.add(previousButton);
		panel.add(toggleButton);
		panel.add(nextButton);
		panel.add(wikiButton);
		panel.add(videoButton);
		panel.add(resetButton);
		return panel;
	}

	private JPanel createTimelinePanel()
	{
		JPanel panel = createCardPanel(true);
		panel.setLayout(new BorderLayout(0, 8));

		JLabel heading = new JLabel("Timeline");
		heading.setFont(FontManager.getRunescapeBoldFont());
		heading.setForeground(Color.WHITE);

		JLabel hint = new JLabel("Click any step to jump there.");
		hint.setFont(FontManager.getRunescapeSmallFont());
		hint.setForeground(MUTED_TEXT_COLOR);

		JPanel headingPanel = new JPanel(new BorderLayout(0, 4));
		headingPanel.setOpaque(false);
		headingPanel.add(heading, BorderLayout.NORTH);
		headingPanel.add(hint, BorderLayout.SOUTH);

		JScrollPane stepScrollPane = new JScrollPane(stepList);
		stepScrollPane.setBorder(BorderFactory.createEmptyBorder());
		stepScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		stepScrollPane.getVerticalScrollBar().setUnitIncrement(16);
		stepScrollPane.setPreferredSize(new Dimension(0, 320));
		stepScrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);

		panel.add(headingPanel, BorderLayout.NORTH);
		panel.add(stepScrollPane, BorderLayout.CENTER);
		return panel;
	}

	private void refreshSectionSelector(List<GuideSection> sections)
	{
		if (sectionSelector.getItemCount() != sections.size())
		{
			sectionSelector.removeAllItems();
			for (GuideSection section : sections)
			{
				sectionSelector.addItem(section.getDisplayTitle());
			}
		}

		int sectionIndex = plugin.getCurrentSectionIndex();
		if (sectionIndex >= 0 && sectionIndex < sectionSelector.getItemCount())
		{
			sectionSelector.setSelectedIndex(sectionIndex);
		}
	}

	private void refreshSummary(GuideSection section, GuideStep currentStep, Set<String> completedIds)
	{
		if (section == null)
		{
			progressPill.setText("No data");
			completionPill.setText("0 completed");
			autoStatusPill.setText("Auto: manual");
			stylePill(progressPill, SURFACE_ALT_COLOR, MUTED_TEXT_COLOR);
			stylePill(completionPill, SURFACE_ALT_COLOR, MUTED_TEXT_COLOR);
			stylePill(autoStatusPill, SURFACE_ALT_COLOR, MUTED_TEXT_COLOR);
			sectionProgressBar.setMinimum(0);
			sectionProgressBar.setMaximum(1);
			sectionProgressBar.setValue(0);
			return;
		}

		int totalSteps = section.getSteps().size();
		int currentPosition = currentStep == null ? 0 : Math.max(0, currentStep.getIndex());
		int completedCount = 0;
		for (GuideStep step : section.getSteps())
		{
			if (completedIds.contains(step.getId()))
			{
				completedCount++;
			}
		}

		progressPill.setText("Step " + currentPosition + " / " + totalSteps);
		completionPill.setText(completedCount + " completed");
		autoStatusPill.setText(plugin.getAutoProgressText().isEmpty() ? "Auto hidden" : plugin.getAutoProgressText());

		stylePill(progressPill, ACCENT_SOFT_COLOR, ACCENT_COLOR);
		stylePill(completionPill, completedCount == totalSteps && totalSteps > 0 ? new Color(42, 83, 54) : SURFACE_ALT_COLOR,
			completedCount == totalSteps && totalSteps > 0 ? SUCCESS_COLOR : MUTED_TEXT_COLOR);
		styleAutoPill(autoStatusPill);

		sectionProgressBar.setMinimum(0);
		sectionProgressBar.setMaximum(Math.max(1, totalSteps));
		sectionProgressBar.setValue(Math.min(Math.max(currentPosition, 0), Math.max(1, totalSteps)));
	}

	private void refreshCurrentStepCard(GuideSection section, GuideStep currentStep, Set<String> completedIds)
	{
		if (currentStep == null)
		{
			currentStepBadge.setText("CURRENT STEP");
			currentStepText.setText("No step loaded.");
			navTargetLabel.setVisible(false);
			toggleButton.setText("Done");
			renderWithdrawLines();
			return;
		}

		boolean completed = completedIds.contains(currentStep.getId());
		currentStepBadge.setText("CURRENT STEP  " + currentStep.getIndex());
		currentStepText.setText(currentStep.getText());
		toggleButton.setText(completed ? "Undo" : "Done");
		toggleButton.setBackground(completed ? WARNING_COLOR : SUCCESS_COLOR);
		toggleButton.setForeground(completed ? Color.BLACK : Color.WHITE);

		StepMetadata.NavTarget nav = plugin.getCurrentNavTarget();
		if (nav != null && nav.getLabel() != null && !nav.getLabel().isEmpty())
		{
			navTargetLabel.setText("Go to: " + nav.getLabel());
			navTargetLabel.setVisible(true);
		}
		else
		{
			navTargetLabel.setVisible(false);
		}

		renderWithdrawLines();
	}

	/**
	 * One line per item of a "Withdraw:" step, coloured by where it is: green on you,
	 * white in the bank, red missing, grey when it has no id to check.
	 */
	private void renderWithdrawLines()
	{
		withdrawPanel.removeAll();
		List<WithdrawLine> lines = plugin.getCurrentWithdrawLines();
		for (WithdrawLine line : lines)
		{
			JLabel label = new JLabel("• " + line.getLabel());
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setForeground(colorForState(line.getState()));
			label.setAlignmentX(Component.LEFT_ALIGNMENT);
			label.setToolTipText(line.getLabel());   // full text on hover if it truncates
			withdrawPanel.add(label);
		}
		withdrawPanel.setVisible(!lines.isEmpty());
		withdrawPanel.revalidate();
		withdrawPanel.repaint();
	}

	private Color colorForState(ItemState state)
	{
		switch (state)
		{
			case CARRIED:
				return SUCCESS_COLOR;
			case BANK:
				return Color.WHITE;
			case MISSING:
				return DANGER_COLOR;
			case UNKNOWN:
				// No id to check against — a token the resolver couldn't map to an item
				// (a role like "Combat runes", or a quest item with no single id). Shown
				// blue as the exact guide text: distinct from red "missing tracked item",
				// so the list still carries every part of the withdraw step to fetch.
				return INFO_COLOR;
			default:
				return MUTED_TEXT_COLOR;
		}
	}

	private void refreshTimeline(GuideStep currentStep)
	{
		listModel.clear();
		for (GuideStep step : plugin.getVisibleSteps())
		{
			listModel.addElement(step);
		}

		if (currentStep != null)
		{
			stepList.setSelectedValue(currentStep, true);
		}
		else
		{
			stepList.clearSelection();
		}
	}

	private JButton createActionButton(String label, Runnable action, Color background, Color foreground)
	{
		JButton button = new JButton(label);
		button.setFocusPainted(false);
		button.setBorderPainted(false);
		button.setOpaque(true);
		button.setBackground(background);
		button.setForeground(foreground);
		button.setMargin(new Insets(4, 6, 4, 6));
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusPainted(false);
		button.addActionListener(event -> action.run());
		return button;
	}

	private JPanel createCardPanel()
	{
		return createCardPanel(false);
	}

	/**
	 * @param fill whether the card should take up spare vertical space. Only the
	 *     timeline should — the header and current-step cards must sit at their
	 *     natural height, or the vertical BoxLayout stretches them and opens a gap
	 *     between the title (pinned top) and the subtitle (pinned bottom of the row).
	 */
	private JPanel createCardPanel(boolean fill)
	{
		JPanel panel = fill ? new JPanel() : new JPanel()
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		panel.setOpaque(true);
		panel.setBackground(SURFACE_COLOR);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORDER_COLOR),
			BorderFactory.createEmptyBorder(10, 10, 10, 10)));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (fill)
		{
			panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		}
		return panel;
	}

	private static JLabel createPillLabel()
	{
		JLabel label = new JLabel();
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setOpaque(true);
		label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		return label;
	}

	private void stylePill(JLabel label, Color background, Color foreground)
	{
		label.setBackground(background);
		label.setForeground(foreground);
	}

	private void styleAutoPill(JLabel label)
	{
		String text = label.getText().toLowerCase();
		if (text.contains("ready"))
		{
			stylePill(label, new Color(42, 83, 54), SUCCESS_COLOR);
		}
		else if (text.contains("tracking"))
		{
			stylePill(label, new Color(52, 70, 94), INFO_COLOR);
		}
		else if (text.contains("disabled"))
		{
			stylePill(label, SURFACE_ALT_COLOR, MUTED_TEXT_COLOR);
		}
		else if (text.contains("waiting"))
		{
			stylePill(label, new Color(86, 63, 34), WARNING_COLOR);
		}
		else
		{
			stylePill(label, SURFACE_ALT_COLOR, MUTED_TEXT_COLOR);
		}
	}

	private static class StepRenderer implements ListCellRenderer<GuideStep>
	{
		private final HcimGuidePlugin plugin;
		private final JPanel panel = new JPanel(new BorderLayout(10, 0));
		private final JLabel indexLabel = new JLabel();
		private final JLabel metaLabel = new JLabel();
		private final JPanel textPanel = new JPanel();
		private final JTextArea titleArea = new JTextArea();
		private final Font smallFont = FontManager.getRunescapeSmallFont();

		private StepRenderer(HcimGuidePlugin plugin)
		{
			this.plugin = plugin;

			indexLabel.setHorizontalAlignment(SwingConstants.CENTER);
			indexLabel.setVerticalAlignment(SwingConstants.CENTER);
			indexLabel.setPreferredSize(new Dimension(34, 34));
			indexLabel.setFont(FontManager.getRunescapeBoldFont());
			indexLabel.setOpaque(true);
			indexLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

			titleArea.setFont(smallFont);
			titleArea.setLineWrap(true);
			titleArea.setWrapStyleWord(true);
			titleArea.setEditable(false);
			titleArea.setFocusable(false);
			titleArea.setOpaque(false);
			titleArea.setBorder(BorderFactory.createEmptyBorder());
			metaLabel.setFont(smallFont);

			textPanel.setOpaque(false);
			textPanel.setLayout(new BorderLayout(0, 2));
			textPanel.add(titleArea, BorderLayout.CENTER);
			textPanel.add(metaLabel, BorderLayout.SOUTH);

			panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
			panel.add(indexLabel, BorderLayout.WEST);
			panel.add(textPanel, BorderLayout.CENTER);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends GuideStep> list, GuideStep step, int index, boolean isSelected, boolean cellHasFocus)
		{
			Set<String> completedIds = plugin.getCompletedStepIds();
			boolean completed = completedIds.contains(step.getId());
			GuideStep currentStep = plugin.getCurrentStep();
			boolean current = currentStep != null && currentStep.getId().equals(step.getId());

			titleArea.setText(buildIndentedText(step.getLevel(), step.getText()));
			metaLabel.setText(completed ? "Completed" : current ? "Current" : "Pending");
			metaLabel.setForeground(current ? ACCENT_COLOR : completed ? SUCCESS_COLOR : MUTED_TEXT_COLOR);

			int textWidth = Math.max(140, list.getWidth() - 90);
			titleArea.setSize(new Dimension(textWidth, Short.MAX_VALUE));

			indexLabel.setText(String.valueOf(step.getIndex()));
			if (completed)
			{
				indexLabel.setBackground(new Color(42, 83, 54));
				indexLabel.setForeground(SUCCESS_COLOR);
			}
			else if (current)
			{
				indexLabel.setBackground(ACCENT_SOFT_COLOR);
				indexLabel.setForeground(ACCENT_COLOR);
			}
			else
			{
				indexLabel.setBackground(SURFACE_ALT_COLOR);
				indexLabel.setForeground(MUTED_TEXT_COLOR);
			}

			if (isSelected || current)
			{
				panel.setBackground(new Color(57, 62, 72));
				titleArea.setForeground(Color.WHITE);
			}
			else
			{
				panel.setBackground(SURFACE_COLOR);
				titleArea.setForeground(new Color(225, 228, 232));
			}
			panel.setOpaque(true);
			return panel;
		}

		private String buildIndentedText(int level, String text)
		{
			StringBuilder builder = new StringBuilder();
			for (int i = 1; i < level; i++)
			{
				builder.append("  -> ");
			}
			builder.append(text);
			return builder.toString();
		}
	}
}
