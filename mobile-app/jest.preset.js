/**
 * Custom jest preset: inherits jest-expo but applies compatibility fixes
 * for the Expo SDK 52 + React Native 0.76 combination.
 *
 * Background: jest-expo 54 was published before RN 0.76 and before some
 * Expo SDK 52 package paths stabilised, causing multiple "Cannot find module"
 * crashes in jest-expo's own setup.js.  We resolve each one here.
 */
const expoPreset = require('jest-expo/jest-preset');

module.exports = {
  ...expoPreset,
  setupFiles: [
    require.resolve('react-native/jest/setup.js'),
    // Patches RN 0.76's NativeModules mock (.default removed) then runs the
    // real jest-expo setup (via require inside the compat file).
    require.resolve('./jest-setup-expo-compat.js'),
  ],
  moduleNameMapper: {
    ...expoPreset.moduleNameMapper,
    // Deep-path expo internals that don't exist in SDK 52 but are
    // referenced by jest-expo 54's setup.js:
    '^expo/src/async-require/messageSocket$': '<rootDir>/jest-mock-empty.js',
    '^expo/src/winter$': '<rootDir>/jest-mock-empty.js',
    '^expo/virtual/streams$': '<rootDir>/jest-mock-empty.js',
    '^expo-modules-core/src/polyfill/dangerous-internal$':
      '<rootDir>/jest-stubs/expo-polyfill-stub.js',
  },
};
