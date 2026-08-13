package com.conde.hcimguide.ui;

import com.conde.hcimguide.HcimGuideConfig;
import com.conde.hcimguide.HcimGuidePlugin;
import com.conde.hcimguide.model.WithdrawItem;
import com.conde.hcimguide.service.WithdrawService;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Text above your character listing what a "Withdraw:" step still needs — the
 * same idea as Shortest Path's "Pick up: ...".
 *
 * <p>Drawn under interfaces on purpose: it belongs to the world, so it should
 * pass behind the bank rather than sit on top of it. The bank markup lives in
 * {@link WithdrawBankOverlay}, which needs the opposite layer — which is why
 * these are two overlays and not one.
 *
 * <p>Disappears once you are carrying everything, so a step you have already
 * packed for stops shouting.
 */
public class WithdrawOverlay extends Overlay
{
	private static final Color TEXT_COLOR = new Color(236, 197, 94);
	private static final int PLAYER_TEXT_HEIGHT = 220;

	private final Client client;
	private final HcimGuidePlugin plugin;
	private final HcimGuideConfig config;
	private final WithdrawService withdrawService;

	@Inject
	private WithdrawOverlay(Client client, HcimGuidePlugin plugin, HcimGuideConfig config,
		WithdrawService withdrawService)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.withdrawService = withdrawService;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.UNDER_WIDGETS);
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

		String text = withdrawService.describe(wanted);
		if (text == null)
		{
			return null;   // already carrying everything
		}

		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return null;
		}

		Point point = local.getCanvasTextLocation(graphics, text, PLAYER_TEXT_HEIGHT);
		if (point != null)
		{
			OverlayUtil.renderTextLocation(graphics, point, text, TEXT_COLOR);
		}
		return null;
	}
}
