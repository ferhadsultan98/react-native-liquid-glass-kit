'use strict';

module.exports = {
  dependency: {
    platforms: {
      android: {
        sourceDir: './android',
        packageImportPath:
          'import com.sultan.liquidglass.LiquidGlassPackage;',
        packageInstance: 'new LiquidGlassPackage()',
      },
      ios: null,
    },
  },
};
