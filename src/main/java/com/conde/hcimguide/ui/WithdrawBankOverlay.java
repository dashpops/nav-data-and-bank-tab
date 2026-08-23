package com.conde.hcimguide.ui;

import com.conde.hcimguide.HcimGuideConfig;
import com.conde.hcimguide.HcimGuidePlugin;
import com.conde.hcimguide.model.WithdrawItem;
import com.conde.hcimguide.service.WithdrawService;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.QuantityFormatter;

/**
 * Marks up the items in an open bank that the current "Withdraw:" step wants.
 *
 * <p>Separate from {@link WithdrawOverlay} because the two need opposite
 * layering: this has to paint <em>over</em> the bank interface, while the text
 * above your character has to stay behind it.
 */
public class WithdrawBankOverlay extends Overlay
{
	/** The yellow the game uses for bank stack sizes, so "/N" reads as part of it. */
	private static final Color QUANTITY_COLOR = new Color(255, 255, 0);

	private final Client client;
	private final HcimGuidePlugin plugin;
	private final HcimGuideConfig config;
	private final WithdrawService withdrawService;
	private final SpriteManager spriteManager;

	@Inject
	private WithdrawBankOverlay(Client client, HcimGuidePlugin plugin, HcimGuideConfig config,
		WithdrawService withdrawService, SpriteManager spriteManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.withdrawService = withdrawService;
		this.spriteManager = spriteManager;
		setPosition(OverlayPosition.DYNAMIC);
		// The bank is an interface, so anything drawn on the scene layer ends up
		// hidden behind it.
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showWithdrawItems())
		{
			return null;
		}

		List<WithdrawItem> wanted = plugin.getCurrentWithdrawItems();
		if (wanted.isEmpty())
		{
			return null;
		}

		// Every item of the step, including ones already satisfied: those are the
		// ones showing a tick, and hiding them would make the tick unreachable.
		highlightBankItems(graphics, withdrawService.allItemIds(wanted));
		return null;
	}

	/**
	 * Marks up every bank slot holding a wanted item. Reads the id straight off
	 * each widget rather than pairing it with the bank container by index: a fully
	 * withdrawn item leaves a placeholder widget but drops out of the container, so
	 * pairing by index would lose it — and losing it is exactly why the tick used
	 * to vanish the moment you had everything.
	 */
	private void highlightBankItems(Graphics2D graphics, Set<Integer> itemIds)
	{
		if (itemIds.isEmpty())
		{
			return;
		}

		Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (container == null || container.isHidden())
		{
			return;
		}

		for (Widget child : container.getDynamicChildren())
		{
			if (child == null || child.isHidden())
			{
				continue;
			}
			int id = withdrawService.canonicalItemId(child.getItemId());
			if (!itemIds.contains(id))
			{
				continue;
			}
			// No box around the item: the count and marker say enough, and a border
			// on every row is noise once the bank is already filtered to these items.
			drawProgress(graphics, child.getBounds(), id, child.getItemQuantity());
		}
	}

	/**
	 * Marks up an item the way Quest Helper does: the required amount written as
	 * "/N" straight after the stack size the bank already draws in the top-left,
	 * with a tick or cross after it, dropping to a second line when the two
	 * numbers will not fit together.
	 *
	 * <p>The marker reflects what you are carrying — that is what says whether the
	 * step is done, and the number on the left is already the bank's own count.
	 */
	private void drawProgress(Graphics2D graphics, Rectangle bounds, int itemId, int bankQuantity)
	{
		WithdrawItem entry = withdrawService.entryFor(itemId, plugin.getCurrentWithdrawItems());
		if (entry == null)
		{
			return;
		}

		int required = entry.getQuantity();
		boolean satisfied = withdrawService.carriedCount(entry) >= Math.max(required, 1);

		// The guide sometimes names a thing without an amount - "Food". Inventing
		// "/1" there would read as an instruction the guide never gave, so only the
		// tick or cross is drawn.
		if (required <= 0)
		{
			BufferedImage only = spriteManager.getSprite(
				satisfied ? SpriteID.Checkbox.CHECKED : SpriteID.Checkbox.CROSSED, 0);
			if (only != null)
			{
				graphics.drawImage(only, bounds.x + bounds.width - only.getWidth(), bounds.y, null);
			}
			return;
		}

		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = graphics.getFontMetrics();

		String stack = QuantityFormatter.quantityToStackSize(Math.max(bankQuantity, 1));
		String goal = "/" + QuantityFormatter.quantityToStackSize(required);
		int stackWidth = metrics.stringWidth(stack);
		int goalWidth = metrics.stringWidth(goal);

		BufferedImage marker = spriteManager.getSprite(
			satisfied ? SpriteID.Checkbox.CHECKED : SpriteID.Checkbox.CROSSED, 0);
		int markerWidth = marker == null ? 0 : marker.getWidth();

		int textX = bounds.x + 1 + stackWidth;
		int textY = bounds.y + metrics.getAscent();
		int markerX = textX + goalWidth + 1;
		int markerY = bounds.y;

		// Too wide for one line: goal underneath, marker beside the stack size.
		if (stackWidth + goalWidth + markerWidth > bounds.width)
		{
			textX = bounds.x + 1;
			textY = bounds.y + metrics.getAscent() + metrics.getHeight() - 2;
			markerX = bounds.x + 1 + stackWidth;
			markerY = bounds.y;
		}

		graphics.setColor(Color.BLACK);
		graphics.drawString(goal, textX + 1, textY + 1);
		graphics.setColor(QUANTITY_COLOR);
		graphics.drawString(goal, textX, textY);

		if (marker != null)
		{
			graphics.drawImage(marker, markerX, markerY, null);
		}
	}
}
