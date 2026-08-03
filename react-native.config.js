'use strict';

module.exports = {
  dependency: {
    platforms: {
      android: {
        sourceDir: './android',
        packageImportPath:
          'import com.liquidglasskit.LiquidGlassPackage;',
        packageInstance: 'new LiquidGlassPackage()',
      },
      ios: null,
    },
  },
};
