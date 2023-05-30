package dev.xf3d3.celestyteams.menuSystem.menu;

import dev.xf3d3.celestyteams.CelestyTeams;
import dev.xf3d3.celestyteams.menuSystem.Menu;
import dev.xf3d3.celestyteams.menuSystem.PlayerMenuUtility;
import dev.xf3d3.celestyteams.menuSystem.paginatedMenu.ClanListGUI;
import dev.xf3d3.celestyteams.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class TeamJoinRequestMenu extends Menu {

    FileConfiguration messagesConfig = CelestyTeams.getPlugin().messagesFileManager.getMessagesConfig();
    FileConfiguration guiConfig = CelestyTeams.getPlugin().teamGUIFileManager.getClanGUIConfig();


    public TeamJoinRequestMenu(PlayerMenuUtility playerMenuUtility){
        super(playerMenuUtility);
    }

    @Override
    public String getMenuName(){
        return ColorUtils.translateColorCodes(guiConfig.getString("team-join.name"));
    }

    @Override
    public int getSlots(){
        return 9;
    }

    @Override
    public void handleMenu(InventoryClickEvent event){
        Player player = (Player) event.getWhoClicked();
        if (event.getCurrentItem().getType().equals(Material.LIME_STAINED_GLASS_PANE)){
            Player targetClanOwner = playerMenuUtility.getOfflineClanOwner().getPlayer();
            if (targetClanOwner != null){
                targetClanOwner.sendMessage(ColorUtils.translateColorCodes(messagesConfig.getString("team-invite-request")
                        .replace("%PLAYER%", player.getName())));
                player.closeInventory();
                player.sendMessage(ColorUtils.translateColorCodes(messagesConfig.getString("team-invite-sent-successfully")
                        .replace("%TEAMOWNER%", targetClanOwner.getName())));
            }else {
                player.closeInventory();
                player.sendMessage(ColorUtils.translateColorCodes(messagesConfig.getString("team-invite-request-failed")));
            }
        }else if (event.getCurrentItem().getType().equals(Material.RED_STAINED_GLASS_PANE)){
            new ClanListGUI(CelestyTeams.getPlugin()).open(player);
        }
    }

    @Override
    public void setMenuItems(){

        ItemStack sendJoinRequestItem = new ItemStack(Material.LIME_STAINED_GLASS_PANE, 1);
        ItemMeta sendMeta = sendJoinRequestItem.getItemMeta();
        sendMeta.setDisplayName(ColorUtils.translateColorCodes(guiConfig.getString("team-join.icons.send-request-name")));
        sendJoinRequestItem.setItemMeta(sendMeta);

        ItemStack cancelJoinRequestItem = new ItemStack(Material.RED_STAINED_GLASS_PANE, 1);
        ItemMeta cancelMeta = sendJoinRequestItem.getItemMeta();
        cancelMeta.setDisplayName(ColorUtils.translateColorCodes(guiConfig.getString("team-join.icons.cancel-request-name")));
        cancelJoinRequestItem.setItemMeta(cancelMeta);

        inventory.setItem(0, sendJoinRequestItem);
        inventory.setItem(8, cancelJoinRequestItem);
    }
}
