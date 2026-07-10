package carpetbotrestriction.mixin;

import carpet.patches.EntityPlayerMPFake;
import carpetbotrestriction.CarpetBotRestriction;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(EntityPlayerMPFake.class)
public class EntityPlayerMPFakeMixin extends ServerPlayer {

    public EntityPlayerMPFakeMixin(MinecraftServer server, ServerLevel world, GameProfile profile, ClientInformation clientOptions) {
        super(server, world, profile, clientOptions);
    }

    /**
     * When an EntityPlayerMPFake is created, register it with the player who created it
     */
    @Inject(
            method = "<init>",
            at = @At("TAIL"),
            remap = false
    )
    private void registerBotOnCreate(MinecraftServer server, ServerLevel world, GameProfile profile, ClientInformation cli, boolean shadow, CallbackInfo ci) {
        // Skip shadow players - they're clones of real players
        if (shadow) return;
        
        ServerPlayer player = CarpetBotRestriction.CREATE_BOT_SOURCE.getPlayer();
        if (player == null) return;
        
        UUID playerID = player.getUUID();
        UUID botID = profile.id();
        
        ObjectOpenHashSet<UUID> players;
        if (!CarpetBotRestriction.PLAYERS.containsKey(playerID)) {
            players = new ObjectOpenHashSet<>();
            CarpetBotRestriction.PLAYERS.put(playerID, players);
        }
        else {
            players = CarpetBotRestriction.PLAYERS.get(playerID);
        }
        players.add(botID);
        CarpetBotRestriction.BOTS.put(botID, playerID);
        CarpetBotRestriction.LOGGER.debug("Assigned bot {} (UUID: {}) to player {}.", profile.name(), botID, player.getName());
    }

    /**
     * Before spawning a bot, check if the player is allowed to create it
     * (e.g., bot cannot impersonate a real player who has logged in)
     */
    @Inject(
            method = "createFake",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void checkIfBotCreateAllowed(
            String username, MinecraftServer server, Vec3 pos, double yaw, double pitch,
            ResourceKey<Level> dimensionId, GameType gamemode, boolean flying,
            @NotNull CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer player = CarpetBotRestriction.CREATE_BOT_SOURCE.getPlayer();
        if (player == null) return;
        
        // Check if the bot about to be spawned is a real player that has logged onto the server
        UUID offlineBotID = UUIDUtil.createOfflinePlayerUUID(username);
        if (!Permissions.check(CarpetBotRestriction.CREATE_BOT_SOURCE, "carpetbotrestriction.admin.create_real", 2) && CarpetBotRestriction.REAL_PLAYERS.contains(offlineBotID)) {
            CarpetBotRestriction.error(CarpetBotRestriction.CREATE_BOT_SOURCE, "You cannot create a bot with this name - it belongs to a real player.");
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
