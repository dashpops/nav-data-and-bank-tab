package com.conde.hcimguide.model;

import net.runelite.api.coords.WorldPoint;

/**
 * Explicit navigation / quest-helper metadata for a single guide step.
 * Loaded from step-metadata.json and takes priority over text-based parsing.
 */
public class StepMetadata
{
	/** Navigation target — null if this step has no movement goal. */
	private NavTarget nav;

	/** Quest Helper quest name — null if this step has no quest to open. */
	private String quest;

	public NavTarget getNav()
	{
		return nav;
	}

	public void setNav(NavTarget nav)
	{
		this.nav = nav;
	}

	public String getQuest()
	{
		return quest;
	}

	public void setQuest(String quest)
	{
		this.quest = quest;
	}

	// ── Inner class ──────────────────────────────────────────────────────────

	public static class NavTarget
	{
		private String label;
		private int x;
		private int y;
		private int z;

		public String getLabel()
		{
			return label;
		}

		public void setLabel(String label)
		{
			this.label = label;
		}

		public int getX()
		{
			return x;
		}

		public void setX(int x)
		{
			this.x = x;
		}

		public int getY()
		{
			return y;
		}

		public void setY(int y)
		{
			this.y = y;
		}

		public int getZ()
		{
			return z;
		}

		public void setZ(int z)
		{
			this.z = z;
		}

		public WorldPoint toWorldPoint()
		{
			return new WorldPoint(x, y, z);
		}
	}
}
