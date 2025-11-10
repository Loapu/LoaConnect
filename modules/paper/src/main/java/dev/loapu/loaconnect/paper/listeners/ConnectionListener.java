package dev.loapu.loaconnect.paper.listeners;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import dev.loapu.loaconnect.paper.LoaConnectPlugin;
import dev.loapu.loaconnect.paper.storage.UserStorage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

public class ConnectionListener implements Listener
{
	private LoaConnectPlugin plugin;
	
	public ConnectionListener(LoaConnectPlugin plugin)
	{
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
		this.plugin = plugin;
	}
	
	@EventHandler
	void onConnectionClose(PlayerConnectionCloseEvent event)
	{
		UUID uniqueId = event.getPlayerUniqueId();
		plugin.removeResponse(uniqueId);
		plugin.removeState(uniqueId);
		UserStorage.removeIfEmpty(uniqueId);
	}
}
