package com.conde.hcimguide.model;

import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

/**
 * Explicit navigation / quest-helper metadata for a single guide step.
 * Loaded from step-metadata.json and takes priority over text-based parsing.
 */
public class StepMetadata
{
	/** Navigation target — null if this step has no movement goal. */
	private NavTarget nav;

	/**
	 * Additional destinations for steps that gather from several places (e.g.
	 * "collect snake weed and ardrigal"). Set this instead of {@link #nav} when a
	 * step has more than one; the plugin routes to whichever is closest.
	 */
	private List<NavTarget> navs;

	/** Quest Helper quest name — null if this step has no quest to open. */
	private String quest;

	/**
	 * Items this step wants out of the bank. Only set on "Withdraw:" steps;
	 * null everywhere else.
	 */
	private List<WithdrawItem> withdraw;

	public NavTarget getNav()
	{
		if (nav != null)
		{
			return nav;
		}
		return navs == null || navs.isEmpty() ? null : navs.get(0);
	}

	public void setNav(NavTarget nav)
	{
		this.nav = nav;
	}

	/** Every destination for this step, in declaration order. Never null. */
	public List<NavTarget> getNavTargets()
	{
		if (navs != null && !navs.isEmpty())
		{
			return navs;
		}
		return nav == null ? Collections.emptyList() : Collections.singletonList(nav);
	}

	public void setNavs(List<NavTarget> navs)
	{
		this.navs = navs;
	}

	public String getQuest()
	{
		return quest;
	}

	/** Never null. */
	public List<WithdrawItem> getWithdraw()
	{
		return withdraw == null ? Collections.emptyList() : withdraw;
	}

	public void setWithdraw(List<WithdrawItem> withdraw)
	{
		this.withdraw = withdraw;
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
