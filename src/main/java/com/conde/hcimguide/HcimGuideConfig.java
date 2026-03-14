package com.conde.hcimguide;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

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
}
