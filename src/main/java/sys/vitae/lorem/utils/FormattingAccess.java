package sys.vitae.lorem.utils;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public final class FormattingAccess {

    public static final String PERMISSION = "loremipsum.format";

    private FormattingAccess() {}

    public static boolean canFormat(Player player) {
        if (player.isPermissionSet(PERMISSION)) {
            return player.hasPermission(PERMISSION);
        }
        return player.isOp() || player.getGameMode() == GameMode.CREATIVE;
    }
}
