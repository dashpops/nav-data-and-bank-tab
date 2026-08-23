package com.conde.hcimguide.service;

import net.runelite.api.Skill;

/**
 * Graded tools the guide names generically — a "Withdraw: ... Pickaxe" step wants
 * <em>a</em> pickaxe, and the best one you can actually use is the right answer,
 * not the bronze the alias table records as a placeholder.
 *
 * <p>Each family lists its tiers low-to-high with the level needed to use the tool
 * (Mining to mine, Woodcutting to chop — wielding needs Attack, but you dig and
 * cut with it in your inventory, so that is the gate that matters). Item ids come
 * from {@code scripts/itemids.py}; the "(or)"/charged variants are included so an
 * ornamented or infernal tool still counts.
 */
public final class ToolFamilies
{
	public static final class Tier
	{
		public final int id;
		public final int level;

		Tier(int id, int level)
		{
			this.id = id;
			this.level = level;
		}
	}

	public static final class Family
	{
		public final String name;
		public final Skill skill;
		public final Tier[] tiers;   // low -> high

		Family(String name, Skill skill, Tier[] tiers)
		{
			this.name = name;
			this.skill = skill;
			this.tiers = tiers;
		}
	}

	public static final Family PICKAXE = new Family("Pickaxe", Skill.MINING, new Tier[]{
		new Tier(1265, 1),   // Bronze
		new Tier(1267, 1),   // Iron
		new Tier(1269, 6),   // Steel
		new Tier(12297, 11), // Black
		new Tier(1273, 21),  // Mithril
		new Tier(1271, 31),  // Adamant
		new Tier(1275, 41),  // Rune
		new Tier(23276, 41), // Gilded
		new Tier(11920, 61), // Dragon
		new Tier(23677, 61), // Dragon (or)
		new Tier(20014, 61), // 3rd age
		new Tier(13243, 61), // Infernal
		new Tier(13244, 61), // Infernal (uncharged)
		new Tier(23680, 71), // Crystal
		new Tier(23682, 71), // Crystal (inactive)
	});

	public static final Family AXE = new Family("Axe", Skill.WOODCUTTING, new Tier[]{
		new Tier(1351, 1),   // Bronze
		new Tier(1349, 1),   // Iron
		new Tier(1353, 6),   // Steel
		new Tier(1361, 11),  // Black
		new Tier(1355, 21),  // Mithril
		new Tier(1357, 31),  // Adamant
		new Tier(1359, 41),  // Rune
		new Tier(23279, 41), // Gilded
		new Tier(6739, 61),  // Dragon
		new Tier(20011, 61), // 3rd age
		new Tier(13241, 61), // Infernal
		new Tier(13242, 61), // Infernal (uncharged)
		new Tier(23673, 71), // Crystal
		new Tier(23675, 71), // Crystal (inactive)
	});

	/** The family a generic "Withdraw:" entry names, by its role name, or null. */
	public static Family byName(String name)
	{
		if (name == null)
		{
			return null;
		}
		String n = name.trim();
		if (n.equalsIgnoreCase("Pickaxe"))
		{
			return PICKAXE;
		}
		if (n.equalsIgnoreCase("Axe"))
		{
			return AXE;
		}
		return null;
	}

	private ToolFamilies()
	{
	}
}
