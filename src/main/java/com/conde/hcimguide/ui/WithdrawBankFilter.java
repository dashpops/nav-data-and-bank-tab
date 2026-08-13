package com.conde.hcimguide.ui;

import com.conde.hcimguide.HcimGuidePlugin;
import com.conde.hcimguide.model.WithdrawItem;
import com.conde.hcimguide.service.WithdrawService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.SpriteID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;

/**
 * A button in the bank that filters it down to the items the current
 * "Withdraw:" step asks for — the same idea as Quest Helper's quest bank tab.
 *
 * <p>The bank rebuilds its item widgets constantly (opening, scrolling,
 * searching, switching tab), which wipes any changes made to them. So rather
 * than filtering once, this re-applies after every rebuild, hooked on
 * {@code BANKMAIN_FINISHBUILDING}. Turning it off restores the widget positions
 * saved when the filter was applied.
 */
@Singleton
public class WithdrawBankFilter
{
	private static final int BUTTON_SIZE = 25;
	private static final int BUTTON_X = 408;
	private static final int BUTTON_Y = 30;      // below Quest Helper's, which sits at y=5
	private static final int ITEMS_PER_ROW = 8;
	private static final int ITEM_X_STEP = 48;
	private static final int ITEM_Y_STEP = 36;
	private static final int ITEM_ROW_START = 51;
	private static final int FIRST_ROW_Y = 0;

	private final Client client;
	private final ClientThread clientThread;
	private final HcimGuidePlugin plugin;
	private final WithdrawService withdrawService;

	private Widget button;
	private Widget buttonIcon;
	private boolean active;

	/** widget index -> {originalX, originalY, hidden} captured before filtering. */
	private final Map<Integer, int[]> savedLayout = new HashMap<>();
	private int savedScrollHeight;
	private String savedTitle;

	@Inject
	private WithdrawBankFilter(Client client, ClientThread clientThread, HcimGuidePlugin plugin,
		WithdrawService withdrawService)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.plugin = plugin;
		this.withdrawService = withdrawService;
	}

	/** Called after the bank finishes building: (re)add the button and re-apply. */
	public void onBankBuilt()
	{
		// The rebuild replaced every widget, so anything saved refers to the old ones.
		savedLayout.clear();
		addButton();
		if (active)
		{
			applyFilter();
		}
	}

	public void reset()
	{
		active = false;
		button = null;
		buttonIcon = null;
		savedLayout.clear();
		savedTitle = null;
	}

	private void addButton()
	{
		Widget parent = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		if (parent == null)
		{
			return;
		}

		button = parent.createChild(-1, WidgetType.GRAPHIC);
		button.setSpriteId(SpriteID.UNKNOWN_BUTTON_SQUARE_SMALL);
		button.setOriginalWidth(BUTTON_SIZE);
		button.setOriginalHeight(BUTTON_SIZE);
		button.setOriginalX(BUTTON_X);
		button.setOriginalY(BUTTON_Y);
		button.setHasListener(true);
		button.setAction(0, active ? "Show all items" : "Show guide items");
		button.setOnOpListener((JavaScriptCallback) e -> toggle());
		button.revalidate();

		buttonIcon = parent.createChild(-1, WidgetType.GRAPHIC);
		buttonIcon.setSpriteId(SpriteID.TAB_QUESTS);
		buttonIcon.setOriginalWidth(BUTTON_SIZE - 6);
		buttonIcon.setOriginalHeight(BUTTON_SIZE - 6);
		buttonIcon.setOriginalX(BUTTON_X + 3);
		buttonIcon.setOriginalY(BUTTON_Y + 3);
		buttonIcon.setOpacity(active ? 0 : 90);
		buttonIcon.revalidate();
	}

	private void toggle()
	{
		active = !active;
		clientThread.invokeLater(() ->
		{
			if (active)
			{
				applyFilter();
			}
			else
			{
				restore();
			}
			addButton();   // refresh the button's label and icon state
		});
	}

	/**
	 * Puts back everything {@link #applyFilter()} changed.
	 *
	 * <p>Done by remembering each widget's position and hidden flag rather than by
	 * asking the game to rebuild the bank: the rebuild script's arguments are not
	 * something I could verify, and getting them wrong breaks the bank interface.
	 * The saved state belongs to the build currently on screen, which is exactly
	 * the one being restored.
	 */
	private void restore()
	{
		Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (container == null)
		{
			savedLayout.clear();
			return;
		}

		Widget[] children = container.getDynamicChildren();
		for (int i = 0; i < children.length; i++)
		{
			int[] saved = savedLayout.get(i);
			Widget child = children[i];
			if (saved == null || child == null)
			{
				continue;
			}
			child.setOriginalX(saved[0]);
			child.setOriginalY(saved[1]);
			child.setHidden(saved[2] == 1);
			child.revalidate();
		}
		savedLayout.clear();

		container.setScrollHeight(savedScrollHeight);
		client.runScript(ScriptID.UPDATE_SCROLLBAR,
			InterfaceID.Bankmain.SCROLLBAR, InterfaceID.Bankmain.ITEMS, 0);

		Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
		if (title != null && savedTitle != null)
		{
			title.setText(savedTitle);
			savedTitle = null;
		}
	}

	/**
	 * Hides bank items the step does not call for and packs the rest into a grid
	 * at the top, so the bank shows only what you came for.
	 */
	private void applyFilter()
	{
		Set<Integer> wanted = wantedItemIds();
		Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (container == null || bank == null)
		{
			return;
		}

		Widget[] children = container.getDynamicChildren();
		Item[] items = bank.getItems();
		int shown = 0;

		if (savedLayout.isEmpty())
		{
			savedScrollHeight = container.getScrollHeight();
			Widget existingTitle = client.getWidget(InterfaceID.Bankmain.TITLE);
			savedTitle = existingTitle == null ? null : existingTitle.getText();
		}

		for (int i = 0; i < children.length; i++)
		{
			Widget child = children[i];
			if (child == null)
			{
				continue;
			}

			savedLayout.putIfAbsent(i,
				new int[]{child.getOriginalX(), child.getOriginalY(), child.isHidden() ? 1 : 0});

			// Children beyond the item count are separators and tab furniture.
			boolean keep = i < items.length && wanted.contains(items[i].getId());
			if (!keep)
			{
				child.setHidden(true);
				continue;
			}

			child.setHidden(false);
			child.setOriginalX(ITEM_ROW_START + (shown % ITEMS_PER_ROW) * ITEM_X_STEP);
			child.setOriginalY(FIRST_ROW_Y + (shown / ITEMS_PER_ROW) * ITEM_Y_STEP);
			child.revalidate();
			shown++;
		}

		// Shrink the scroll area to what is left, or the bank scrolls into blank space.
		int rows = (shown + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
		container.setScrollHeight(Math.max(rows * ITEM_Y_STEP, container.getHeight()));
		client.runScript(ScriptID.UPDATE_SCROLLBAR,
			InterfaceID.Bankmain.SCROLLBAR, InterfaceID.Bankmain.ITEMS, 0);

		Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
		if (title != null)
		{
			title.setText(shown > 0
				? "Guide items (<col=ff9040>" + shown + "</col>)"
				: "Guide items — <col=ff4040>none in bank</col>");
		}
	}

	/** Item IDs the current step still needs; empty when nothing is outstanding. */
	private Set<Integer> wantedItemIds()
	{
		List<WithdrawItem> wanted = plugin.getCurrentWithdrawItems();
		return withdrawService.outstandingItemIds(wanted);
	}

	public boolean isActive()
	{
		return active;
	}
}
