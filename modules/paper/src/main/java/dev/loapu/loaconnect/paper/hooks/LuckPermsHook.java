package dev.loapu.loaconnect.paper.hooks;

import dev.loapu.loaconnect.paper.LoaConnectPlugin;
import dev.loapu.loaconnect.paper.authentication.UserInfoUtil;
import dev.loapu.loaconnect.paper.storage.UserStorage;
import dev.loapu.loaconnect.paper.transactions.Transaction;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class LuckPermsHook
{
	private static LuckPermsHook instance;
	private final LoaConnectPlugin plugin;
	private LuckPerms luckPerms;
	private final boolean enabled;
	
	
	public static LuckPermsHook instance()
	{
		return instance == null ? instance = new LuckPermsHook() : instance;
	}
	
	private LuckPermsHook()
	{
		plugin = LoaConnectPlugin.instance();
		enabled = plugin.configProvider().general().syncGroups();
	}
	
	public void init()
	{
		if (!enabled) return;
		
		RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
		if (provider == null)
		{
			plugin.getComponentLogger().error("The sync-groups feature is enabled but LuckPerms is not installed!");
			plugin.getComponentLogger().error("Please install LuckPerms or disable the sync-groups feature. Shutting down...");
			Bukkit.getPluginManager().disablePlugin(plugin);
			return;
		}
		luckPerms = provider.getProvider();
	}
	
	public void syncGroups(UserStorage user)
	{
		if (!enabled) return;
		Transaction transaction = new Transaction();
		UUID uniqueId = user.uniqueId();
		Player player = plugin.getServer().getPlayer(uniqueId);
		String display = player == null ? uniqueId.toString() : player.getName();
		plugin.getServer().getAsyncScheduler().runNow(plugin, task ->
		{
			
			transaction.info("Syncing groups for {}...", display);
			List<String> groups = UserInfoUtil.instance().groups(user);
			if (groups == null || groups.isEmpty()) return;
			transaction.info("Fetched groups from idp.");
			User luckPermsUser = luckPerms.getUserManager().getUser(uniqueId);
			if (luckPermsUser == null) return;
			transaction.info("Fetched LuckPerms user.");
			String groupPrefix = plugin.configProvider().general().groupPrefix();
			Collection<InheritanceNode> userGroups = luckPermsUser.getNodes(NodeType.INHERITANCE);
			for (InheritanceNode node : userGroups)
			{
				luckPermsUser.data().remove(node);
				transaction.info("Removed user from group {}", node.getGroupName());
			}
			for (String group : groups)
			{
				if (!groupPrefix.isEmpty() && !group.startsWith(groupPrefix)) continue;
				group = group.substring(groupPrefix.length());
				Group luckPermsGroup = luckPerms.getGroupManager().getGroup(group);
				if (luckPermsGroup == null) continue;
				Node groupNode = InheritanceNode.builder(group).build();
				DataMutateResult result = luckPermsUser.data().add(groupNode);
				if (!result.wasSuccessful())
				{
					transaction.warn("Failed to add user to group {}", group);
					continue;
				}
				transaction.info("Added user to group {}", group);
			}
			luckPerms.getUserManager().saveUser(luckPermsUser);
			transaction.end();
		});
	}
}
