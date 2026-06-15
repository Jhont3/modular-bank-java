const path = require('path')
const HtmlWebpackPlugin = require('html-webpack-plugin')
const { ModuleFederationPlugin } = require('webpack').container

// URLs de los remotes resueltas en runtime (el bundle del shell NO contiene
// código de los MFEs). En compose apuntan al proxy del portal (:8085/mfe/...).
const NOTIFICATIONS_MFE_URL = process.env.NOTIFICATIONS_MFE_URL || 'http://localhost:8187'
const TRANSFERS_MFE_URL = process.env.TRANSFERS_MFE_URL || 'http://localhost:8188'

module.exports = {
  entry: './src/index.js',
  mode: 'development',
  devServer: {
    port: 8186,
    historyApiFallback: true,
    headers: { 'Access-Control-Allow-Origin': '*' },
  },
  output: {
    publicPath: 'auto',
    path: path.resolve(__dirname, 'dist'),
  },
  resolve: { extensions: ['.jsx', '.js'] },
  module: {
    rules: [
      {
        test: /\.jsx?$/,
        exclude: /node_modules/,
        use: {
          loader: 'babel-loader',
          options: { presets: ['@babel/preset-env', '@babel/preset-react'] },
        },
      },
      { test: /\.css$/, use: ['style-loader', 'css-loader'] },
    ],
  },
  plugins: [
    new ModuleFederationPlugin({
      name: 'shell',
      remotes: {
        notifications: `notifications@${NOTIFICATIONS_MFE_URL}/remoteEntry.js`,
        transfers: `transfers@${TRANSFERS_MFE_URL}/remoteEntry.js`,
      },
      shared: {
        react: { singleton: true, strictVersion: true, requiredVersion: '^18.2.0' },
        'react-dom': { singleton: true, strictVersion: true, requiredVersion: '^18.2.0' },
        'react-router-dom': { singleton: true, strictVersion: true, requiredVersion: '^6.22.0' },
      },
    }),
    new HtmlWebpackPlugin({ template: './public/index.html' }),
  ],
}
