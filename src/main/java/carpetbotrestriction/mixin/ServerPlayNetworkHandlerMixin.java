package carpetbotrestriction.mixin;

import carpetbotrestriction.CarpetBotRestriction;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow public abstract ServerPlayer getPlayer();

    /**
     * If a bot disconnects, remove from lists
     */
    @Inject(
            method = "onDisconnect",
            at = @At("HEAD")
    )
    private void logDisconnect(DisconnectionDetails info, CallbackInfo ci) {
        UUID entity = this.getPlayer().getUUID();
        UUID player = CarpetBotRestriction.BOTS.remove(entity);
        // If disconnected player is a bot, remove it from the lists
        if (player != null) {
            ObjectOpenHashSet<UUID> bots = CarpetBotRestriction.PLAYERS.get(player);
            if (bots == null) return;
            bots.remove(entity);
            return;
        }
        // If disconnected player is a real player, then if removeOnDisconnect is true, then
        // kill all the player's bots
        if (!CarpetBotRestriction.CONFIG.get("removeOnDisconnect", false)) return;
        ObjectOpenHashSet<UUID> bots = CarpetBotRestriction.PLAYERS.get(entity);
        if (bots == null) return;
        MinecraftServer mc = this.getPlayer().level().getServer();
        if (mc == null) return;
        PlayerList playerManager = mc.getPlayerList();
        if (playerManager == null) return;
        for (UUID bot : bots) {
            if (bot == null) continue;
            ServerPlayer toRemove = playerManager.getPlayer(bot);
            if (toRemove == null) continue;
            toRemove.kill(toRemove.level());
            bots.remove(bot);
        }
        if (bots.isEmpty()) {
            CarpetBotRestriction.PLAYERS.remove(entity);
        }
    }
}
