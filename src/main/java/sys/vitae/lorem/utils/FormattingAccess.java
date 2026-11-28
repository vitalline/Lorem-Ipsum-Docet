package sys.vitae.lorem.utils;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public final class FormattingAccess {

    public static final String FORMAT_PERMISSION = "loremipsum.format";
    public static final String CHAT_PERMISSION = "loremipsum.chat";

    private FormattingAccess() {}

    public static boolean canFormat(Player player) {
        if (player.isPermissionSet(FORMAT_PERMISSION)) {
            return player.hasPermission(FORMAT_PERMISSION);
        }
        return player.isOp() || player.getGameMode() == GameMode.CREATIVE;
    }
}
