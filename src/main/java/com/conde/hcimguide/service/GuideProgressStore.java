package com.conde.hcimguide.service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.client.config.ConfigManager;

public class GuideProgressStore
{
	private static final String GROUP = "hcimguide";
	private static final String SECTION_KEY = "currentSectionIndex";
	private static final String STEP_KEY = "currentStepIndex";
	private static final String COMPLETED_KEY = "completedStepIds";

	private final ConfigManager configManager;

	public GuideProgressStore(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	public int getCurrentSectionIndex()
	{
		return getInt(SECTION_KEY, 0);
	}

	public void setCurrentSectionIndex(int value)
	{
		configManager.setConfiguration(GROUP, SECTION_KEY, Math.max(0, value));
	}

	public int getCurrentStepIndex()
	{
		return getInt(STEP_KEY, 0);
	}

	public void setCurrentStepIndex(int value)
	{
		configManager.setConfiguration(GROUP, STEP_KEY, Math.max(0, value));
	}

	public Set<String> getCompletedStepIds()
	{
		String raw = configManager.getConfiguration(GROUP, COMPLETED_KEY);
		if (raw == null || raw.trim().isEmpty())
		{
			return new LinkedHashSet<>();
		}

		return Arrays.stream(raw.split(","))
			.map(String::trim)
			.filter(value -> !value.isEmpty())
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	public void setCompletedStepIds(Set<String> stepIds)
	{
		String raw = stepIds.stream().collect(Collectors.joining(","));
		configManager.setConfiguration(GROUP, COMPLETED_KEY, raw);
	}

	public void reset()
	{
		configManager.unsetConfiguration(GROUP, SECTION_KEY);
		configManager.unsetConfiguration(GROUP, STEP_KEY);
		configManager.unsetConfiguration(GROUP, COMPLETED_KEY);
	}

	private int getInt(String key, int fallback)
	{
		String value = configManager.getConfiguration(GROUP, key);
		if (value == null)
		{
			return fallback;
		}

		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException ex)
		{
			return fallback;
		}
	}
}
