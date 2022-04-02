# Marketwatch
Market data for the Qortal blockchain

Marketwatch is a highly customizable, yet easy to use utility that extracts every trade straight from your local Qortal node and plots that data into charts. It also calculates cross-chain and USD prices from that data since the day the trade portal went live and until the present time.

Features include:

- Multiple price or trade charts can be arranged on a desktop using drag and drop or by using pre-configured layouts and arrangements.
- The charts and user interface style can be custimized to your liking with many options for colors, fonts and other specific charting features.
- Anyone can create layouts, arrangements or styles and share them with the community.
- Separate trades tab for every cross-chain pair where you can see every trade ever made plotted on a chart. Simply click on a trade to select it, then double click for more info.
- Trade info will auto-update every 15 minutes by default. You can choose to disable auto-updates and do it manually. The Qortal core must be online and accessible to get the latest trades. To get the latest US dollar prices an internet connection is required.
- A node monitor is included to inform you of your node's status. You can also get info about your minting account here, such as your level, balance, blocks minted and an estimation of when you will reach the next level (based on your actual minting efficiency)
- Prices are determined by a weighted average, this helps mitigate price manipulation attempts by listing and buying up small amounts of QORT for prices well below or above market price, as can clearly be seen in the trades charts. Due to the weighted average algorithm, these fluctuations are filtered out. For Litecoin, a weighted average of 35 trades is used, for Bitcoin and Dogecoin, due to the much lower trading activity, 10 trades are averaged.

System requirements:

- Marketwatch should work on any desktop (or laptop) environment that has Java OpenJDK 11 or higher installed (Windows, Mac and Linux).
- In order to extract the latest trades from your node, the app must run on the same system as your Qortal core, or an SSH tunnel to the system running your core must be opened.
- Marketwatch's RAM and CPU usage are dependant on how many charts are open simultaneously. The app should work on most systems, but on slower systems (such as the Raspberry Pi) the user experience may not be optimal.
