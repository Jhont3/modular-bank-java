const path = require('path')
const HtmlWebpackPlugin = require('html-webpack-plugin')
const { VueLoaderPlugin } = require('vue-loader')
const { ModuleFederationPlugin } = require('webpack').container

module.exports = {
  entry: './src/index.js',
  mode: 'development',
  devServer: {
    port: 8188,
    headers: { 'Access-Control-Allow-Origin': '*' },
  },
  output: {
    publicPath: 'auto',
    path: path.resolve(__dirname, 'dist'),
  },
  resolve: { extensions: ['.js', '.vue'] },
  module: {
    rules: [
      { test: /\.vue$/, use: 'vue-loader' },
      {
        test: /\.js$/,
        exclude: /node_modules/,
        use: {
          loader: 'babel-loader',
          options: { presets: ['@babel/preset-env'] },
        },
      },
      { test: /\.css$/, use: ['style-loader', 'css-loader'] },
    ],
  },
  plugins: [
    new VueLoaderPlugin(),
    new ModuleFederationPlugin({
      name: 'transfers',
      filename: 'remoteEntry.js',
      exposes: {
        './TransfersApp': './src/mount',
      },
      shared: {
        vue: { singleton: true, strictVersion: true, requiredVersion: '^3.4.0' },
      },
    }),
    new HtmlWebpackPlugin({ template: './public/index.html' }),
  ],
}
