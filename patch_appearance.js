const fs = require('fs');

let kt = fs.readFileSync('app/src/main/java/com/neubofy/reality/ui/activity/AppearanceActivity.kt', 'utf8');

const setupPresetsKt = `
    private fun setupPresets() {
        binding.presetCinematic.setOnClickListener {
            selectedAccent = ThemeManager.AccentColor.TEAL
            selectedPattern = ThemeManager.BackgroundPattern.ZEN
            binding.modeDark.isChecked = true
            binding.switchAmoled.isChecked = true
            binding.chipGlassMediumV2.isChecked = true
            binding.styleGlassV2.isChecked = true

            // Set legacy and mode-specific colors
            binding.inputAppBg.setText("#05050A")
            binding.inputPopupBg.setText("#1A1A24")
            binding.inputModePageBg.setText("#05050A")
            binding.inputModeCardBg.setText("#1AFFFFFF")

            updateAccentSelectionUI()
            updatePatternSelectionUI()
            android.widget.Toast.makeText(this, "Cinematic Preset Applied", android.widget.Toast.LENGTH_SHORT).show()
        }

        binding.presetMinimalist.setOnClickListener {
            selectedAccent = ThemeManager.AccentColor.BLUE
            selectedPattern = ThemeManager.BackgroundPattern.NONE
            binding.modeLight.isChecked = true
            binding.switchAmoled.isChecked = false
            binding.chipGlassSubtleV2.isChecked = true
            binding.styleOutlinedV2.isChecked = true

            binding.inputAppBg.setText("#FFFFFF")
            binding.inputPopupBg.setText("#F5F5F5")
            binding.inputModePageBg.setText("#FFFFFF")
            binding.inputModeCardBg.setText("#FFFFFF")

            updateAccentSelectionUI()
            updatePatternSelectionUI()
            android.widget.Toast.makeText(this, "Minimalist Preset Applied", android.widget.Toast.LENGTH_SHORT).show()
        }

        binding.presetCyberpunk.setOnClickListener {
            selectedAccent = ThemeManager.AccentColor.PINK
            selectedPattern = ThemeManager.BackgroundPattern.GRADIENT
            binding.modeDark.isChecked = true
            binding.switchAmoled.isChecked = true
            binding.chipGlassStrongV2.isChecked = true
            binding.styleFilledV2.isChecked = true

            binding.inputAppBg.setText("#000000")
            binding.inputPopupBg.setText("#0D0115")
            binding.inputModePageBg.setText("#000000")
            binding.inputModeCardBg.setText("#1A000000")

            updateAccentSelectionUI()
            updatePatternSelectionUI()
            android.widget.Toast.makeText(this, "Cyberpunk Preset Applied", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
`;

kt = kt.replace(/private fun setupPresets\(\) \{[\s\S]*?android\.widget\.Toast\.makeText\(this, "Cyberpunk Preset Applied", android\.widget\.Toast\.LENGTH_SHORT\)\.show\(\)\n        \}\n    \}/, setupPresetsKt.trim());

fs.writeFileSync('app/src/main/java/com/neubofy/reality/ui/activity/AppearanceActivity.kt', kt);
