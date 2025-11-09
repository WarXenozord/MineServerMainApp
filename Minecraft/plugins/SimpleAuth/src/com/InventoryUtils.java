package com.simpleauth;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.Base64;

public class InventoryUtils {

    public static String toBase64(PlayerInventory inventory) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeInt(inventory.getContents().length);
            for (ItemStack item : inventory.getContents()) {
                dataOutput.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void fromBase64(PlayerInventory inventory, String base64) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            ItemStack[] contents = new ItemStack[dataInput.readInt()];
            for (int i = 0; i < contents.length; i++) {
                contents[i] = (ItemStack) dataInput.readObject();
            }
            inventory.setContents(contents);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}