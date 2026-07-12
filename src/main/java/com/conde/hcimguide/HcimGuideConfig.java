package com.conde.hcimguide;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("hcimguide")
public interface HcimGuideConfig extends Config
{
	@ConfigItem(
		keyName = "enableAutoProgress",
		name = "Enable auto-progress",
		description = "Auto-complete the current step when a safe quest, skill, or key-item condition is detected"
	)
	default boolean enableAutoProgress()
	{
		return true;
	}

	@ConfigItem(
		keyName = "advanceWhenAutoCompleted",
		name = "Advance on auto-complete",
		description = "Move to the next step when the current step is auto-completed"
	)
	default boolean advanceWhenAutoCompleted()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showCompletedSteps",
		name = "Show completed steps",
		description = "Keep completed steps visible in the sidebar list"
	)
	default boolean showCompletedSteps()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showAutoStatus",
		name = "Show auto status",
		description = "Display the current auto-progress rule in the sidebar"
	)
	default boolean showAutoStatus()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showStepOverlay",
		name = "Show step overlay",
		description = "Draw the current step on screen (hold Alt to move it)"
	)
	default boolean showStepOverlay()
	{
		return true;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "overlayOpacity",
		name = "Overlay opacity",
		description = "Background opacity of the on-screen step overlay, in percent"
	)
	default int overlayOpacity()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "enableShortestPath",
		name = "Shortest Path integration",
		description = "Send the current step's destination to the Shortest Path plugin, when installed"
	)
	default boolean enableShortestPath()
	{
		return true;
	}
}
