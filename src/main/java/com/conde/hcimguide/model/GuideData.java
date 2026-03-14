package com.conde.hcimguide.model;

import java.util.Collections;
import java.util.List;

public class GuideData
{
	private String title;
	private String sourceUrl;
	private String generatedAt;
	private List<GuideSection> sections;

	public String getTitle()
	{
		return title;
	}

	public String getSourceUrl()
	{
		return sourceUrl;
	}

	public String getGeneratedAt()
	{
		return generatedAt;
	}

	public List<GuideSection> getSections()
	{
		return sections == null ? Collections.emptyList() : sections;
	}
}
