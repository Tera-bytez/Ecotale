package com.ecotale.lib.simplehud;

import javax.annotation.Nonnull;

/**
 * Configuration for auto-sizing a panel based on text content.
 * 
 * Originally from SimpleHUD-Lib by Terabytez, inlined into Ecotale.
 */
public class AutoSizeConfig {
    
    /** The element ID to watch for text changes */
    @Nonnull
    public final String textElementId;
    
    /** The panel element ID whose width will be adjusted */
    @Nonnull
    public final String panelElementId;
    
    /** Base width in pixels (minimum width when text is empty) */
    public final int baseWidth;
    
    /** Width per character in pixels */
    public final float charWidth;
    
    /** Extra padding to add to calculated width */
    public final int padding;
    
    /** Maximum allowed width */
    public final int maxWidth;
    
    public AutoSizeConfig(
            @Nonnull String textElementId,
            @Nonnull String panelElementId,
            int baseWidth,
            float charWidth,
            int padding,
            int maxWidth) {
        this.textElementId = textElementId;
        this.panelElementId = panelElementId;
        this.baseWidth = baseWidth;
        this.charWidth = charWidth;
        this.padding = padding;
        this.maxWidth = maxWidth;
    }
    
    /**
     * Create a config with sensible defaults.
     */
    public static AutoSizeConfig withDefaults(
            @Nonnull String textElementId,
            @Nonnull String panelElementId) {
        return new AutoSizeConfig(
            textElementId,
            panelElementId,
            40,     // baseWidth
            10f,    // charWidth
            20,     // padding
            300     // maxWidth
        );
    }
    
    /**
     * Calculate the required width for the given text.
     */
    public int calculateWidth(String text) {
        if (text == null || text.isEmpty()) {
            return baseWidth + padding;
        }
        
        int textWidth = (int) (text.length() * charWidth);
        int totalWidth = baseWidth + textWidth + padding;
        
        return Math.min(totalWidth, maxWidth);
    }
}
