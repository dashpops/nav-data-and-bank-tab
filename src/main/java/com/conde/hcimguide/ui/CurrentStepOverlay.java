package com.conde.hcimguide.ui;

import com.conde.hcimguide.HcimGuideConfig;
import com.conde.hcimguide.HcimGuidePlugin;
import com.conde.hcimguide.model.GuideSection;
import com.conde.hcimguide.model.GuideStep;
import com.conde.hcimguide.model.StepMetadata;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * On-screen card with the current guide step. Defaults to the top-left corner;
 * like any RuneLite overlay, hold Alt to drag it anywhere.
 */
public class CurrentStepOverlay extends OverlayPanel
{
	private static final int PANEL_WIDTH = 236;
	private static final Color ACCENT_COLOR = new Color(224, 169, 72);
	private static final Color MUTED_TEXT_COLOR = new Color(198, 203, 211);
	private static final Color NAV_COLOR = new Color(126, 178, 226);
	private static final Color BACKGROUND_COLOR = new Color(24, 26, 31);

	private final HcimGuidePlugin plugin;
	private final HcimGuideConfig config;

	@Inject
	private CurrentStepOverlay(HcimGuidePlugin plugin, HcimGuideConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setMovable(true);
		panelComponent.setPreferredSize(new Dimension(PANEL_WIDTH, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showStepOverlay())
		{
			return null;
		}

		GuideStep step = plugin.getCurrentStep();
		if (step == null)
		{
			return null;
		}

		int alpha = Math.max(0, Math.min(100, config.overlayOpacity())) * 255 / 100;
		panelComponent.setBackgroundColor(new Color(
			BACKGROUND_COLOR.getRed(), BACKGROUND_COLOR.getGreen(), BACKGROUND_COLOR.getBlue(), alpha));

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("HCIM Guide")
			.color(ACCENT_COLOR)
			.build());

		GuideSection section = plugin.getCurrentSection();
		if (section != null && section.getTitle() != null && !section.getTitle().isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(section.getTitle())
				.leftColor(MUTED_TEXT_COLOR)
				.build());
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left(step.getText())
			.leftColor(Color.WHITE)
			.build());

		StepMetadata.NavTarget nav = plugin.getCurrentNavTarget();
		if (nav != null && nav.getLabel() != null && !nav.getLabel().isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Go to: " + nav.getLabel())
				.leftColor(NAV_COLOR)
				.build());
		}

		return super.render(graphics);
	}
}
