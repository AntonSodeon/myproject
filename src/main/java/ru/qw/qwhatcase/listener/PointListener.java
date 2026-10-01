package ru.qw.qwhatcase.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.gui.CaseContentMenu;
import ru.qw.qwhatcase.storage.CasePoint;
import ru.qw.qwhatcase.util.Placeholders;

import java.util.List;

/** ПКМ по назначенному блоку открывает меню кейса (ключ не списывается); блок защищён от разрушения. */
public final class PointListener implements Listener {
    private final QWHatCasePlugin plugin;

    public PointListener(QWHatCasePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void interact(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        CasePoint point = plugin.points().at(event.getClickedBlock());
        if (point == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("qwhatcase.cases")) {
            plugin.messages().send(player, "error.no-permission");
            return;
        }
        if (plugin.catalog().caseDef(point.caseId()).filter(c -> c.enabled()).isEmpty()) {
            plugin.messages().send(player, "cases.disabled");
            return;
        }
        new CaseContentMenu(plugin, player, point.caseId(), 0).open();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void breakBlock(BlockBreakEvent event) {
        if (plugin.points().at(event.getBlock()) != null) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), event.getPlayer().hasPermission("qwhatcase.admin.points")
                    ? "point.break-admin" : "point.break-denied", Placeholders.of());
        }
    }

    private boolean protectedAny(List<Block> blocks) {
        for (Block block : blocks) {
            if (plugin.points().at(block) != null) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void entityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> plugin.points().at(b) != null);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void blockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> plugin.points().at(b) != null);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void pistonExtend(BlockPistonExtendEvent event) {
        if (protectedAny(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void pistonRetract(BlockPistonRetractEvent event) {
        if (protectedAny(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void burn(BlockBurnEvent event) {
        if (plugin.points().at(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void change(EntityChangeBlockEvent event) {
        if (plugin.points().at(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }
}
