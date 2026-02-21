/**
 * Compatibility shim for jest-expo + React Native 0.76.
 *
 * jest-expo's setup.js reads `require('...NativeModules').default`, but
 * RN 0.76's jest mock no longer has a `.default` property — it returns
 * the modules object directly.  We simply add .default = self to the
 * existing mock so jest-expo's Object.defineProperty calls don't crash.
 */
'use strict';

const nativeModulesMock = jest.requireMock(
  'react-native/Libraries/BatchedBridge/NativeModules',
);

if (nativeModulesMock && typeof nativeModulesMock === 'object' && !nativeModulesMock.default) {
  nativeModulesMock.default = nativeModulesMock;
}

// Run the real jest-expo setup (now that .default exists)
require('jest-expo/src/preset/setup');
