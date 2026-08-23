package com.conde.hcimguide.service;

import com.conde.hcimguide.model.ItemState;
import com.conde.hcimguide.model.WithdrawItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;

/**
 * Works out which items of a "Withdraw:" step you are still short of.
 *
 * <p>Counts what you are carrying — inventory and worn equipment — and subtracts
 * it, so a step stops nagging once you have the items. An entry naming
 * alternatives ("Food": wine or karambwan) counts them together, because any of
 * them satisfies it.
 */
@Singleton
public class WithdrawService
{
	private final Client client;
	private final ItemManager itemManager;
	private final Map<Integer, Integer> bankSnapshot = new HashMap<>();

	@Inject
	public WithdrawService(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	/**
	 * The real item an id stands for, seeing through a bank placeholder. The guide
	 * tells you to keep placeholders on, so an item you fully withdraw leaves a
	 * placeholder behind — a different id that maps back to the real one. Resolving
	 * it is what lets a withdrawn item keep its slot, and its tick, in the bank.
	 *
	 * <p>Must be called on the client thread.
	 */
	public int canonicalItemId(int itemId)
	{
		if (itemId <= 0)
		{
			return itemId;
		}
		ItemComposition comp = itemManager.getItemComposition(itemId);
		return comp.getPlaceholderTemplateId() != -1 ? comp.getPlaceholderId() : itemId;
	}

	/** How many of the given item IDs the player is carrying, worn items included. */
	private int carried(Set<Integer> itemIds)
	{
		int total = 0;
		for (int containerId : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer container = client.getItemContainer(containerId);
			if (container == null)
			{
				continue;
			}
			for (Item item : container.getItems())
			{
				if (itemIds.contains(item.getId()))
				{
					total += Math.max(1, item.getQuantity());
				}
			}
		}
		return total + runePouchCount(itemIds);
	}

	/**
	 * Runes stored in the rune pouch count as carried too — otherwise a step wanting
	 * Law runes reads as missing when they are sitting in the pouch. Each rune-slot
	 * varbit holds an index into the {@code RUNEPOUCH_RUNE} enum, which maps to the
	 * rune's item id; the paired amount varbit is the count.
	 */
	private int runePouchCount(Set<Integer> itemIds)
	{
		EnumComposition runes = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		if (runes == null)
		{
			return 0;
		}
		int[] runeSlots = {Varbits.RUNE_POUCH_RUNE1, Varbits.RUNE_POUCH_RUNE2, Varbits.RUNE_POUCH_RUNE3,
			Varbits.RUNE_POUCH_RUNE4, Varbits.RUNE_POUCH_RUNE5, Varbits.RUNE_POUCH_RUNE6};
		int[] amountSlots = {Varbits.RUNE_POUCH_AMOUNT1, Varbits.RUNE_POUCH_AMOUNT2, Varbits.RUNE_POUCH_AMOUNT3,
			Varbits.RUNE_POUCH_AMOUNT4, Varbits.RUNE_POUCH_AMOUNT5, Varbits.RUNE_POUCH_AMOUNT6};
		int total = 0;
		for (int i = 0; i < runeSlots.length; i++)
		{
			int amount = client.getVarbitValue(amountSlots[i]);
			if (amount <= 0)
			{
				continue;
			}
			int runeKey = client.getVarbitValue(runeSlots[i]);
			if (runeKey > 0 && itemIds.contains(runes.getIntValue(runeKey)))
			{
				total += amount;
			}
		}
		return total;
	}

	/** Count of the given ids across the bank as well as inventory and worn. */
	private int ownedAnywhere(Set<Integer> itemIds)
	{
		int total = carried(itemIds);
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank != null)
		{
			for (Item item : bank.getItems())
			{
				if (itemIds.contains(item.getId()))
				{
					total += Math.max(1, item.getQuantity());
				}
			}
		}
		return total;
	}

	/**
	 * The highest-tier tool of this family you own (bank, inventory or worn) and can
	 * actually use at your level, or -1 if you have none you could use. This is what a
	 * generic "Pickaxe"/"Axe" step should point you at — the rune pickaxe once you can
	 * mine with it, not the bronze the alias table records as a placeholder.
	 */
	private int bestOwnedUsableId(ToolFamilies.Family fam)
	{
		int level = client.getRealSkillLevel(fam.skill);
		for (int i = fam.tiers.length - 1; i >= 0; i--)
		{
			ToolFamilies.Tier tier = fam.tiers[i];
			if (tier.level <= level && ownedAnywhere(Collections.singleton(tier.id)) > 0)
			{
				return tier.id;
			}
		}
		return -1;
	}

	/**
	 * The ids to show and tick for an entry: the single best usable tool for a generic
	 * tool role, otherwise the entry's own alternatives.
	 */
	public Set<Integer> displayIds(WithdrawItem entry)
	{
		ToolFamilies.Family fam = ToolFamilies.byName(entry.getName());
		if (fam != null)
		{
			int best = bestOwnedUsableId(fam);
			return best > 0 ? Collections.singleton(best) : Collections.emptySet();
		}
		return new HashSet<>(entry.getItemIds());
	}

	/** The entry a bank item satisfies, tool roles included, or null. */
	public WithdrawItem entryFor(int itemId, List<WithdrawItem> wanted)
	{
		if (wanted != null)
		{
			for (WithdrawItem entry : wanted)
			{
				if (displayIds(entry).contains(itemId))
				{
					return entry;
				}
			}
		}
		return null;
	}

	/** Carried count for an entry, counting any usable tier of a tool role together. */
	private int usableCarried(WithdrawItem entry)
	{
		ToolFamilies.Family fam = ToolFamilies.byName(entry.getName());
		if (fam == null)
		{
			return carried(new HashSet<>(entry.getItemIds()));
		}
		int level = client.getRealSkillLevel(fam.skill);
		Set<Integer> usable = new HashSet<>();
		for (ToolFamilies.Tier tier : fam.tiers)
		{
			if (tier.level <= level)
			{
				usable.add(tier.id);
			}
		}
		return carried(usable);
	}

	/** How many of this entry's items you are carrying, counting alternatives together. */
	public int carriedCount(WithdrawItem item)
	{
		return usableCarried(item);
	}

	/**
	 * Snapshot the current bank contents so item states survive the bank closing —
	 * {@code getItemContainer(BANK)} does not keep a usable copy once the interface is
	 * shut, which made every "in bank" item flip back to red. Call whenever the bank
	 * container changes. A spurious empty read (e.g. on close) is ignored so the last
	 * good snapshot stands. Client thread only.
	 */
	public void snapshotBank()
	{
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank == null)
		{
			return;
		}
		Map<Integer, Integer> fresh = new HashMap<>();
		for (Item item : bank.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				fresh.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		if (fresh.isEmpty() && !bankSnapshot.isEmpty())
		{
			return;
		}
		bankSnapshot.clear();
		bankSnapshot.putAll(fresh);
	}

	/** Count of the given ids in the last-seen bank snapshot. */
	private int bankCount(Set<Integer> ids)
	{
		int total = 0;
		for (Integer id : ids)
		{
			Integer count = bankSnapshot.get(id);
			if (count != null)
			{
				total += count;
			}
		}
		return total;
	}

	/**
	 * Where this entry's item currently is, for the sidebar: on you ({@code CARRIED}),
	 * in the bank ({@code BANK}), or neither ({@code MISSING}); {@code UNKNOWN} when it
	 * has no id to check. Tool roles use their best usable tier. Client thread only.
	 */
	public ItemState stateOf(WithdrawItem entry)
	{
		ToolFamilies.Family fam = ToolFamilies.byName(entry.getName());
		if (entry.getItemIds().isEmpty() && fam == null)
		{
			return ItemState.UNKNOWN;
		}
		int need = entry.getQuantity() > 0 ? entry.getQuantity() : 1;
		int carried = usableCarried(entry);
		if (carried >= need)
		{
			return ItemState.CARRIED;
		}
		Set<Integer> ids;
		if (fam != null)
		{
			ids = new HashSet<>();
			int level = client.getRealSkillLevel(fam.skill);
			for (ToolFamilies.Tier tier : fam.tiers)
			{
				if (tier.level <= level)
				{
					ids.add(tier.id);
				}
			}
		}
		else
		{
			ids = new HashSet<>(entry.getItemIds());
		}
		return carried + bankCount(ids) >= need ? ItemState.BANK : ItemState.MISSING;
	}

	/**
	 * The concrete item name(s) an entry resolves to, for the sidebar's coloured list —
	 * so the author's shorthand ("Law", "Pickaxe", "Teleport runes") is spelled out as
	 * "Law rune", the best pickaxe you own, or "Staff of air / Rune pouch / Fire rune".
	 * Falls back to the shorthand when there is nothing to resolve. Client thread only.
	 */
	public String resolvedLabel(WithdrawItem entry)
	{
		String prefix = entry.getQuantity() > 0 ? entry.getQuantity() + " x " : "";
		ToolFamilies.Family fam = ToolFamilies.byName(entry.getName());
		if (fam != null)
		{
			int best = bestOwnedUsableId(fam);
			return prefix + (best > 0 ? itemName(best) : entry.getName());
		}
		List<Integer> ids = entry.getItemIds();
		if (ids.isEmpty())
		{
			return prefix + entry.getName();
		}
		LinkedHashSet<String> names = new LinkedHashSet<>();
		for (int id : ids)
		{
			names.add(itemName(id));
		}
		if (names.size() > 1)
		{
			// Collapse charge/dose variants — "Games necklace(1)".."(8)" become one
			// "Games necklace" line rather than eight. Only when they share a base.
			LinkedHashSet<String> bases = new LinkedHashSet<>();
			for (String n : names)
			{
				bases.add(n.replaceAll("\\(\\d+\\)$", "").trim());
			}
			if (bases.size() == 1)
			{
				return prefix + bases.iterator().next();
			}
		}
		return prefix + String.join(" / ", names);
	}

	private String itemName(int id)
	{
		String name = itemManager.getItemComposition(id).getName();
		return name == null || name.isEmpty() ? "item " + id : name;
	}

	/**
	 * Entries still outstanding for this step. Empty means you already have
	 * everything, in which case nothing should be drawn.
	 *
	 * <p>Must be called on the client thread — it reads item containers.
	 */
	public List<WithdrawItem> outstanding(List<WithdrawItem> wanted)
	{
		if (wanted == null || wanted.isEmpty())
		{
			return Collections.emptyList();
		}

		List<WithdrawItem> missing = new ArrayList<>();
		for (WithdrawItem item : wanted)
		{
			// No IDs recorded and not a tool role we can expand: we cannot check, so always show.
			if (item.getItemIds().isEmpty() && ToolFamilies.byName(item.getName()) == null)
			{
				missing.add(item);
				continue;
			}
			int have = usableCarried(item);
			// Quantity 0 means "some, amount unstated" - one is enough to satisfy it.
			int need = item.getQuantity() > 0 ? item.getQuantity() : 1;
			if (have < need)
			{
				missing.add(item);
			}
		}
		return missing;
	}

	/**
	 * Every item ID the step mentions, satisfied or not. The bank filter uses this
	 * rather than {@link #outstandingItemIds}: an item that vanishes the moment you
	 * pick it up never gets to show its tick.
	 */
	public Set<Integer> allItemIds(List<WithdrawItem> wanted)
	{
		Set<Integer> ids = new HashSet<>();
		if (wanted != null)
		{
			for (WithdrawItem item : wanted)
			{
				ids.addAll(displayIds(item));
			}
		}
		return ids;
	}

	/** Every item ID that would help satisfy the outstanding entries. */
	public Set<Integer> outstandingItemIds(List<WithdrawItem> wanted)
	{
		Set<Integer> ids = new HashSet<>();
		for (WithdrawItem item : outstanding(wanted))
		{
			ids.addAll(displayIds(item));
		}
		return ids;
	}

	/** "Withdraw: 4 x Flax, Food" — null when there is nothing outstanding. */
	public String describe(List<WithdrawItem> wanted)
	{
		List<WithdrawItem> missing = outstanding(wanted);
		if (missing.isEmpty())
		{
			return null;
		}
		List<String> parts = new ArrayList<>(missing.size());
		for (WithdrawItem item : missing)
		{
			parts.add(item.describe());
		}
		return "Withdraw: " + String.join(", ", parts);
	}
}
