package com.conde.hcimguide.model;

import java.util.Collections;
import java.util.List;

public class GuideSection
{
	private String id;
	private String episodeTitle;
	private String title;
	private String youtubeId;
	private List<GuideStep> steps;

	public String getId()
	{
		return id;
	}

	public String getEpisodeTitle()
	{
		return episodeTitle;
	}

	public String getTitle()
	{
		return title;
	}

	public String getYoutubeId()
	{
		return youtubeId;
	}

	public List<GuideStep> getSteps()
	{
		return steps == null ? Collections.emptyList() : steps;
	}

	public String getDisplayTitle()
	{
		if (title != null && !title.isEmpty())
		{
			return title;
		}

		return episodeTitle == null ? "" : episodeTitle;
	}
}
