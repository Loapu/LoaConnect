package dev.loapu.loaconnect.paper.listeners;

import dev.loapu.loaconnect.paper.LoaConnectPlugin;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

public class DialogListener implements Listener
{
	LoaConnectPlugin plugin;
	
	public DialogListener(LoaConnectPlugin plugin)
	{
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
		this.plugin = plugin;
	}
	
	@EventHandler
	void onHandleDialog(PlayerCustomClickEvent event)
	{
		if (!(event.getCommonConnection() instanceof PlayerConfigurationConnection configurationConnection)) return;
		
		UUID uniqueId = configurationConnection.getProfile().getId();
		if (uniqueId == null) return;
		
		Key key = event.getIdentifier();
		if (key.equals(Key.key("loaconnect:auth/deny"))) plugin.setConnectionJoinResult(uniqueId, false);
	}
}
