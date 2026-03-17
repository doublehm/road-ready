import React from 'react';
import { View } from 'react-native';

const WebView = React.forwardRef((props, ref) =>
  React.createElement(View, { ...props, ref, testID: props.testID || 'webview' })
);
WebView.displayName = 'WebView';

module.exports = { WebView };
