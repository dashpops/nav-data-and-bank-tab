package com.conde.hcimguide.service;

import com.conde.hcimguide.model.GuideStep;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

public class GuideAutoProgressService
{
	private static final Pattern OPTIONAL_PATTERN = Pattern.compile("(?i)^optional:");
	private static final Pattern COMPLETE_QUEST_PATTERN = Pattern.compile("(?i)^(?:complete|finish)\\s+(.+?)(?:\\s+quest)?(?:[\\.,]|$)");
	private static final Pattern ITEM_FOCUS_PATTERN = Pattern.compile("(?i)^(?:buy|obtain|get|keep|wear|wield|equip|collect|take|grab|purchase|once you have|have)\\b");
	private static final Pattern SKILL_PATTERN = Pattern.compile(
		"(?i)\\b(\\d{1,2})\\s+(Attack|Strength|Defence|Defense|Hitpoints|Ranged|Prayer|Magic|Cooking|Woodcutting|Fletching|Fishing|Firemaking|Crafting|Smithing|Mining|Herblore|Agility|Thieving|Slayer|Farming|Runecraft|Runecrafting|Hunter|Construction)\\b"
	);

	private static final InventoryID[] TRACKED_CONTAINERS = {
		InventoryID.INVENTORY,
		InventoryID.EQUIPMENT,
		InventoryID.BANK
	};

	private final Map<String, Quest> questLookup = buildQuestLookup();
	private final Map<String, Skill> skillLookup = buildSkillLookup();
	private final List<KeyItemRule> keyItemRules = buildKeyItemRules();

	public Optional<Evaluation> evaluate(Client client, GuideStep step)
	{
		if (client == null || step == null || step.getText() == null)
		{
			return Optional.empty();
		}

		String text = step.getText().trim();
		if (text.isEmpty() || OPTIONAL_PATTERN.matcher(text).find())
		{
			return Optional.empty();
		}

		Quest quest = resolveQuestCompletion(text);
		if (quest != null)
		{
			return Optional.of(new Evaluation("quest complete: " + quest.getName(), quest.getState(client) == QuestState.FINISHED));
		}

		List<KeyItemRule> keyItems = resolveKeyItems(text);
		if (!keyItems.isEmpty())
		{
			boolean satisfied = keyItems.stream().allMatch(rule -> hasAnyTrackedItem(client, rule.getItemIds()));
			String description = "key items: " + keyItems.stream()
				.map(KeyItemRule::getLabel)
				.collect(Collectors.joining(", "));
			return Optional.of(new Evaluation(description, satisfied));
		}

		List<SkillRequirement> skillRequirements = resolveSkillRequirements(text);
		if (!skillRequirements.isEmpty())
		{
			boolean satisfied = skillRequirements.stream()
				.allMatch(requirement -> client.getRealSkillLevel(requirement.getSkill()) >= requirement.getLevel());
			String description = "skills: " + skillRequirements.stream()
				.map(SkillRequirement::toDisplayString)
				.collect(Collectors.joining(", "));
			return Optional.of(new Evaluation(description, satisfied));
		}

		return Optional.empty();
	}

	private Quest resolveQuestCompletion(String text)
	{
		Matcher matcher = COMPLETE_QUEST_PATTERN.matcher(text);
		if (!matcher.find())
		{
			return null;
		}

		String candidate = matcher.group(1);
		return questLookup.get(normalize(candidate));
	}

	private List<KeyItemRule> resolveKeyItems(String text)
	{
		if (!ITEM_FOCUS_PATTERN.matcher(text).find())
		{
			return Collections.emptyList();
		}

		String normalizedText = normalize(text);
		List<KeyItemRule> matches = new ArrayList<>();
		for (KeyItemRule rule : keyItemRules)
		{
			if (normalizedText.contains(rule.getAlias()))
			{
				matches.add(rule);
			}
		}

		return matches;
	}

	private List<SkillRequirement> resolveSkillRequirements(String text)
	{
		Matcher matcher = SKILL_PATTERN.matcher(text);
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		while (matcher.find())
		{
			int level = Integer.parseInt(matcher.group(1));
			Skill skill = skillLookup.get(normalize(matcher.group(2)));
			if (skill == null)
			{
				continue;
			}

			Integer current = levels.get(skill);
			if (current == null || level > current)
			{
				levels.put(skill, level);
			}
		}

		return levels.entrySet().stream()
			.map(entry -> new SkillRequirement(entry.getKey(), entry.getValue()))
			.collect(Collectors.toList());
	}

	private boolean hasAnyTrackedItem(Client client, int[] itemIds)
	{
		for (InventoryID inventoryID : TRACKED_CONTAINERS)
		{
			ItemContainer container = client.getItemContainer(inventoryID);
			if (container == null)
			{
				continue;
			}

			for (int itemId : itemIds)
			{
				if (container.contains(itemId))
				{
					return true;
				}
			}
		}

		return false;
	}

	private Map<String, Quest> buildQuestLookup()
	{
		Map<String, Quest> lookup = new HashMap<>();
		for (Quest quest : Quest.values())
		{
			registerQuestAlias(lookup, quest.getName(), quest);
			registerQuestAlias(lookup, stripLeadingArticle(quest.getName()), quest);
		}

		registerQuestAlias(lookup, "mournings end pt 1", Quest.MOURNINGS_END_PART_I);
		registerQuestAlias(lookup, "mournings end pt 2", Quest.MOURNINGS_END_PART_II);
		registerQuestAlias(lookup, "dragon slayer 1", Quest.DRAGON_SLAYER_I);
		registerQuestAlias(lookup, "dragon slayer 2", Quest.DRAGON_SLAYER_II);
		registerQuestAlias(lookup, "desert treasure 1", Quest.DESERT_TREASURE_I);
		registerQuestAlias(lookup, "desert treasure 2", Quest.DESERT_TREASURE_II__THE_FALLEN_EMPIRE);
		registerQuestAlias(lookup, "monkey madness 1", Quest.MONKEY_MADNESS_I);
		registerQuestAlias(lookup, "monkey madness 2", Quest.MONKEY_MADNESS_II);
		registerQuestAlias(lookup, "recipe for disaster", Quest.RECIPE_FOR_DISASTER);
		return lookup;
	}

	private void registerQuestAlias(Map<String, Quest> lookup, String alias, Quest quest)
	{
		String normalized = normalize(alias);
		if (!normalized.isEmpty())
		{
			lookup.put(normalized, quest);
		}
	}

	private Map<String, Skill> buildSkillLookup()
	{
		Map<String, Skill> lookup = new HashMap<>();
		for (Skill skill : Skill.values())
		{
			lookup.put(normalize(skill.getName()), skill);
		}

		lookup.put("defense", Skill.DEFENCE);
		lookup.put("runecrafting", Skill.RUNECRAFT);
		return lookup;
	}

	private List<KeyItemRule> buildKeyItemRules()
	{
		List<KeyItemRule> rules = new ArrayList<>();
		rules.add(new KeyItemRule("ghostspeak amulet", "ghostspeak amulet", ItemID.GHOSTSPEAK_AMULET, ItemID.GHOSTSPEAK_AMULET_4250));
		rules.add(new KeyItemRule("chef s hat", "chef's hat", ItemID.CHEFS_HAT, ItemID.GOLDEN_CHEFS_HAT));
		rules.add(new KeyItemRule("forestry kit", "forestry kit", ItemID.FORESTRY_KIT));
		rules.add(new KeyItemRule("dramen staff", "dramen staff", ItemID.DRAMEN_STAFF));
		rules.add(new KeyItemRule("ibans staff", "iban's staff", ItemID.IBANS_STAFF, ItemID.IBANS_STAFF_1410, ItemID.IBANS_STAFF_U));
		rules.add(new KeyItemRule("royal seed pod", "royal seed pod", ItemID.ROYAL_SEED_POD));
		rules.add(new KeyItemRule("zombie monkey greegree", "zombie monkey greegree", ItemID.ZOMBIE_MONKEY_GREEGREE, ItemID.ZOMBIE_MONKEY_GREEGREE_4030));
		rules.add(new KeyItemRule("barrows gloves", "barrows gloves", ItemID.BARROWS_GLOVES, ItemID.BARROWS_GLOVES_23593));
		rules.add(new KeyItemRule("rune pouch", "rune pouch", ItemID.RUNE_POUCH, ItemID.RUNE_POUCH_23650, ItemID.RUNE_POUCH_27086, ItemID.RUNE_POUCH_30692, ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_L));
		rules.add(new KeyItemRule("ring of suffering", "ring of suffering", ItemID.RING_OF_SUFFERING, ItemID.RING_OF_SUFFERING_I, ItemID.RING_OF_SUFFERING_R, ItemID.RING_OF_SUFFERING_RI, ItemID.RING_OF_SUFFERING_I_25246, ItemID.RING_OF_SUFFERING_RI_25248, ItemID.RING_OF_SUFFERING_I_26761, ItemID.RING_OF_SUFFERING_RI_26762));
		rules.add(new KeyItemRule("necklace of anguish", "necklace of anguish", ItemID.NECKLACE_OF_ANGUISH, ItemID.NECKLACE_OF_ANGUISH_OR, ItemID.NECKLACE_OF_ANGUISH_27172));
		rules.add(new KeyItemRule("dragon warhammer", "dragon warhammer", ItemID.DRAGON_WARHAMMER, ItemID.DRAGON_WARHAMMER_20785, ItemID.DRAGON_WARHAMMER_OR, ItemID.DRAGON_WARHAMMER_CR));
		rules.add(new KeyItemRule("amulet of glory", "amulet of glory", ItemID.AMULET_OF_GLORY, ItemID.AMULET_OF_GLORY1, ItemID.AMULET_OF_GLORY2, ItemID.AMULET_OF_GLORY3, ItemID.AMULET_OF_GLORY4, ItemID.AMULET_OF_GLORY5, ItemID.AMULET_OF_GLORY6, ItemID.AMULET_OF_GLORY_T, ItemID.AMULET_OF_GLORY_T1, ItemID.AMULET_OF_GLORY_T2, ItemID.AMULET_OF_GLORY_T3, ItemID.AMULET_OF_GLORY_T4, ItemID.AMULET_OF_GLORY_T5, ItemID.AMULET_OF_GLORY_T6, ItemID.AMULET_OF_GLORY_8283, ItemID.AMULET_OF_GLORY_20586));
		rules.add(new KeyItemRule("graceful hood", "graceful hood", ItemID.GRACEFUL_HOOD, ItemID.GRACEFUL_HOOD_11851, ItemID.GRACEFUL_HOOD_13579, ItemID.GRACEFUL_HOOD_13580, ItemID.GRACEFUL_HOOD_13591, ItemID.GRACEFUL_HOOD_13592, ItemID.GRACEFUL_HOOD_13603, ItemID.GRACEFUL_HOOD_13604, ItemID.GRACEFUL_HOOD_13615, ItemID.GRACEFUL_HOOD_13616, ItemID.GRACEFUL_HOOD_13627, ItemID.GRACEFUL_HOOD_13628, ItemID.GRACEFUL_HOOD_13667, ItemID.GRACEFUL_HOOD_13668, ItemID.GRACEFUL_HOOD_21061, ItemID.GRACEFUL_HOOD_21063, ItemID.GRACEFUL_HOOD_24743, ItemID.GRACEFUL_HOOD_24745, ItemID.GRACEFUL_HOOD_25069, ItemID.GRACEFUL_HOOD_25071, ItemID.GRACEFUL_HOOD_27444, ItemID.GRACEFUL_HOOD_27446, ItemID.GRACEFUL_HOOD_30045, ItemID.GRACEFUL_HOOD_30047));
		rules.add(new KeyItemRule("graceful cape", "graceful cape", ItemID.GRACEFUL_CAPE, ItemID.GRACEFUL_CAPE_11853, ItemID.GRACEFUL_CAPE_13581, ItemID.GRACEFUL_CAPE_13582, ItemID.GRACEFUL_CAPE_13593, ItemID.GRACEFUL_CAPE_13594, ItemID.GRACEFUL_CAPE_13605, ItemID.GRACEFUL_CAPE_13606, ItemID.GRACEFUL_CAPE_13617, ItemID.GRACEFUL_CAPE_13618, ItemID.GRACEFUL_CAPE_13629, ItemID.GRACEFUL_CAPE_13630, ItemID.GRACEFUL_CAPE_13669, ItemID.GRACEFUL_CAPE_13670, ItemID.GRACEFUL_CAPE_21064, ItemID.GRACEFUL_CAPE_21066, ItemID.GRACEFUL_CAPE_24746, ItemID.GRACEFUL_CAPE_24748, ItemID.GRACEFUL_CAPE_25072, ItemID.GRACEFUL_CAPE_25074, ItemID.GRACEFUL_CAPE_27447, ItemID.GRACEFUL_CAPE_27449, ItemID.GRACEFUL_CAPE_30048, ItemID.GRACEFUL_CAPE_30050));
		rules.add(new KeyItemRule("graceful top", "graceful top", ItemID.GRACEFUL_TOP, ItemID.GRACEFUL_TOP_11855, ItemID.GRACEFUL_TOP_13583, ItemID.GRACEFUL_TOP_13584, ItemID.GRACEFUL_TOP_13595, ItemID.GRACEFUL_TOP_13596, ItemID.GRACEFUL_TOP_13607, ItemID.GRACEFUL_TOP_13608, ItemID.GRACEFUL_TOP_13619, ItemID.GRACEFUL_TOP_13620, ItemID.GRACEFUL_TOP_13631, ItemID.GRACEFUL_TOP_13632, ItemID.GRACEFUL_TOP_13671, ItemID.GRACEFUL_TOP_13672, ItemID.GRACEFUL_TOP_21067, ItemID.GRACEFUL_TOP_21069, ItemID.GRACEFUL_TOP_24749, ItemID.GRACEFUL_TOP_24751, ItemID.GRACEFUL_TOP_25075, ItemID.GRACEFUL_TOP_25077, ItemID.GRACEFUL_TOP_27450, ItemID.GRACEFUL_TOP_27452, ItemID.GRACEFUL_TOP_30051, ItemID.GRACEFUL_TOP_30053));
		rules.add(new KeyItemRule("graceful legs", "graceful legs", ItemID.GRACEFUL_LEGS, ItemID.GRACEFUL_LEGS_11857, ItemID.GRACEFUL_LEGS_13585, ItemID.GRACEFUL_LEGS_13586, ItemID.GRACEFUL_LEGS_13597, ItemID.GRACEFUL_LEGS_13598, ItemID.GRACEFUL_LEGS_13609, ItemID.GRACEFUL_LEGS_13610, ItemID.GRACEFUL_LEGS_13621, ItemID.GRACEFUL_LEGS_13622, ItemID.GRACEFUL_LEGS_13633, ItemID.GRACEFUL_LEGS_13634, ItemID.GRACEFUL_LEGS_13673, ItemID.GRACEFUL_LEGS_13674, ItemID.GRACEFUL_LEGS_21070, ItemID.GRACEFUL_LEGS_21072, ItemID.GRACEFUL_LEGS_24752, ItemID.GRACEFUL_LEGS_24754, ItemID.GRACEFUL_LEGS_25078, ItemID.GRACEFUL_LEGS_25080, ItemID.GRACEFUL_LEGS_27453, ItemID.GRACEFUL_LEGS_27455, ItemID.GRACEFUL_LEGS_30054, ItemID.GRACEFUL_LEGS_30056));
		rules.add(new KeyItemRule("graceful gloves", "graceful gloves", ItemID.GRACEFUL_GLOVES, ItemID.GRACEFUL_GLOVES_11859, ItemID.GRACEFUL_GLOVES_13587, ItemID.GRACEFUL_GLOVES_13588, ItemID.GRACEFUL_GLOVES_13599, ItemID.GRACEFUL_GLOVES_13600, ItemID.GRACEFUL_GLOVES_13611, ItemID.GRACEFUL_GLOVES_13612, ItemID.GRACEFUL_GLOVES_13623, ItemID.GRACEFUL_GLOVES_13624, ItemID.GRACEFUL_GLOVES_13635, ItemID.GRACEFUL_GLOVES_13636, ItemID.GRACEFUL_GLOVES_13675, ItemID.GRACEFUL_GLOVES_13676, ItemID.GRACEFUL_GLOVES_21073, ItemID.GRACEFUL_GLOVES_21075, ItemID.GRACEFUL_GLOVES_24755, ItemID.GRACEFUL_GLOVES_24757, ItemID.GRACEFUL_GLOVES_25081, ItemID.GRACEFUL_GLOVES_25083, ItemID.GRACEFUL_GLOVES_27456, ItemID.GRACEFUL_GLOVES_27458, ItemID.GRACEFUL_GLOVES_30057, ItemID.GRACEFUL_GLOVES_30059));
		rules.add(new KeyItemRule("graceful boots", "graceful boots", ItemID.GRACEFUL_BOOTS, ItemID.GRACEFUL_BOOTS_11861, ItemID.GRACEFUL_BOOTS_13589, ItemID.GRACEFUL_BOOTS_13590, ItemID.GRACEFUL_BOOTS_13601, ItemID.GRACEFUL_BOOTS_13602, ItemID.GRACEFUL_BOOTS_13613, ItemID.GRACEFUL_BOOTS_13614, ItemID.GRACEFUL_BOOTS_13625, ItemID.GRACEFUL_BOOTS_13626, ItemID.GRACEFUL_BOOTS_13637, ItemID.GRACEFUL_BOOTS_13638, ItemID.GRACEFUL_BOOTS_13677, ItemID.GRACEFUL_BOOTS_13678, ItemID.GRACEFUL_BOOTS_21076, ItemID.GRACEFUL_BOOTS_21078, ItemID.GRACEFUL_BOOTS_24758, ItemID.GRACEFUL_BOOTS_24760, ItemID.GRACEFUL_BOOTS_25084, ItemID.GRACEFUL_BOOTS_25086, ItemID.GRACEFUL_BOOTS_27459, ItemID.GRACEFUL_BOOTS_27461, ItemID.GRACEFUL_BOOTS_30060, ItemID.GRACEFUL_BOOTS_30062));
		return rules;
	}

	private String stripLeadingArticle(String text)
	{
		return text.replaceFirst("^(?i)(the|a|an)\\s+", "");
	}

	private String normalize(String text)
	{
		return text.toLowerCase()
			.replace("&", " and ")
			.replaceAll("[^a-z0-9]+", " ")
			.trim()
			.replaceAll("\\s+", " ");
	}

	public static final class Evaluation
	{
		private final String description;
		private final boolean satisfied;

		private Evaluation(String description, boolean satisfied)
		{
			this.description = description;
			this.satisfied = satisfied;
		}

		public String getDescription()
		{
			return description;
		}

		public boolean isSatisfied()
		{
			return satisfied;
		}
	}

	private static final class KeyItemRule
	{
		private final String alias;
		private final String label;
		private final int[] itemIds;

		private KeyItemRule(String alias, String label, int... itemIds)
		{
			this.alias = alias;
			this.label = label;
			this.itemIds = Arrays.copyOf(itemIds, itemIds.length);
		}

		private String getAlias()
		{
			return alias;
		}

		private String getLabel()
		{
			return label;
		}

		private int[] getItemIds()
		{
			return Arrays.copyOf(itemIds, itemIds.length);
		}
	}

	private static final class SkillRequirement
	{
		private final Skill skill;
		private final int level;

		private SkillRequirement(Skill skill, int level)
		{
			this.skill = skill;
			this.level = level;
		}

		private Skill getSkill()
		{
			return skill;
		}

		private int getLevel()
		{
			return level;
		}

		private String toDisplayString()
		{
			return level + " " + skill.getName();
		}
	}
}
