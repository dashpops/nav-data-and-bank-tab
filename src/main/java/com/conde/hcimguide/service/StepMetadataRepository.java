package com.conde.hcimguide.service;

import com.conde.hcimguide.model.StepMetadata;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class StepMetadataRepository
{
	private static final Logger log = LoggerFactory.getLogger(StepMetadataRepository.class);
	private static final String RESOURCE = "/step-metadata.json";

	private final Gson gson;
	private volatile Map<String, StepMetadata> data = Collections.emptyMap();

	@Inject
	public StepMetadataRepository(Gson gson)
	{
		this.gson = gson;
	}

	public void load()
	{
		try (InputStream in = getClass().getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				log.warn("step-metadata.json not found on classpath");
				return;
			}

			Type type = new TypeToken<Map<String, StepMetadata>>()
			{
			}.getType();
			Map<String, StepMetadata> loaded = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
			data = loaded == null ? Collections.emptyMap() : loaded;
			log.debug("Loaded step metadata: {} entries", data.size());
		}
		catch (Exception e)
		{
			log.warn("Failed to load step-metadata.json", e);
		}
	}

	/**
	 * Returns explicit metadata for the given step ID, or null if none is defined.
	 */
	public StepMetadata get(String stepId)
	{
		return data.get(stepId);
	}

	public int size()
	{
		return data.size();
	}
}
