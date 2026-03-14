package com.conde.hcimguide.service;

import com.conde.hcimguide.model.GuideData;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class GuideRepository
{
	private static final String RESOURCE_PATH = "/guide.json";
	private final Gson gson = new Gson();

	public GuideData load() throws IOException
	{
		InputStream inputStream = GuideRepository.class.getResourceAsStream(RESOURCE_PATH);
		if (inputStream == null)
		{
			throw new IOException("Missing resource " + RESOURCE_PATH);
		}

		try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8))
		{
			return gson.fromJson(reader, GuideData.class);
		}
	}
}
