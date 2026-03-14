package com.conde.hcimguide;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class HcimGuidePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(HcimGuidePlugin.class);
		RuneLite.main(args);
	}
}
