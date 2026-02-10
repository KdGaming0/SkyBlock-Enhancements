package com.github.kd_gaming1.skyblockenhancements.mixin.access;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSignEditScreen.class)
public interface AbstractSignEditScreenAccessor {

    @Invoker("onDone")
    void sbe$invokeOnDone();

    @Accessor("messages")
    String[] sbe$getMessages();
}
