/* Delegates vent-item placement to the server placement utility and mirrors PortalMod's hold-modifier tooltip convention. */
package io.github.bengman.pneumaticdiversityvents.shared;

import io.github.bengman.pneumaticdiversityvents.server.world.utils.VentPlacementUtil;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Util;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.portalmod.core.config.PortalModConfigManager;

import javax.annotation.Nullable;

import java.util.List;

public class VentItem extends BlockItem {
    public VentItem(VentBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    public ActionResultType place(BlockItemUseContext context) {
        return VentPlacementUtil.place(context, (VentBlock) getBlock());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        super.appendHoverText(stack, world, tooltip, flag);
        if (PortalModConfigManager.TOOLTIPS != null && !PortalModConfigManager.TOOLTIPS.get()) return;
        String path = getRegistryName() == null ? "vent_segment" : getRegistryName().getPath();
        if (!Screen.hasControlDown()) {
            String modifier = Util.getPlatform() == Util.OS.OSX ? "Command" : "Ctrl";
            tooltip.add(new TranslationTextComponent("tooltip.pneumaticdiversityvents.hold_control", modifier).withStyle(TextFormatting.DARK_GRAY));
            return;
        }
        String base = "tooltip.pneumaticdiversityvents." + path;
        if (I18n.exists(base)) {
            tooltip.add(new TranslationTextComponent(base).withStyle(TextFormatting.GRAY));
            return;
        }

        for (int i = 1; ; i++) {
            String key = base + "_" + i;
            if (!I18n.exists(key)) break;
            tooltip.add(new TranslationTextComponent(key).withStyle(TextFormatting.GRAY));
        }
    }
}
