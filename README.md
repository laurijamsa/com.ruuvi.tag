# RuuviTag Android Application
The app will do many cool things in the future, but for a start, let's add a support for Eddystone beacons.

### How it works and what it does?
1. RuuviTag broadcasts Eddystone beacon packets
2. Android application shows the info

### Custom data in Eddystone protocol
At the moment there is no Google's support for custom frames in Eddystone protocol specifications. However, this is going to change and it'll be possible to add custom data.

### Identifying if it's a RuuviTag
Because custom data frames won't include ID information about the beacon, the frame has to be linked to UID/URL frame. If the URL frame says _http://ruuvi.com/something_ we know that it's a beacon provided by Ruuvi. And if it's a beacon provided by Ruuvi, CUSTOM0 data frame includes

- Temperature
- Humidity
- Pressure
- Acceleration
- Interrupts