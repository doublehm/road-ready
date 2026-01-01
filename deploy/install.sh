#!/bin/bash

# Exit on error
set -e

echo "🚗 Starting Road Ready Deployment..."

# 1. Install System Dependencies
echo "📦 Installing System Packages..."
sudo apt-get update
sudo apt-get install -y python3-pip python3-venv nginx git

# 2. Setup User and Directory
echo "👤 Setting up User..."
if ! id "roadready" &>/dev/null; then
    sudo useradd -m -s /bin/bash roadready
fi

# Create app directory if not exists
sudo mkdir -p /opt/road-ready
sudo chown -R roadready:www-data /opt/road-ready

# 3. Copy Application Files
# (Assumes you are running this script from inside the uploaded project folder)
echo "📂 Copying Files..."
sudo cp -r . /opt/road-ready/
sudo chown -R roadready:www-data /opt/road-ready

# 4. Setup Virtual Environment
echo "🐍 Setting up Python Virtual Environment..."
sudo -u roadready bash -c "cd /opt/road-ready && python3 -m venv venv && ./venv/bin/pip install -r requirements.txt"

# 5. Configure Nginx
echo "🌐 Configuring Nginx..."
sudo cp deploy/nginx.conf /etc/nginx/sites-available/roadready
sudo ln -sf /etc/nginx/sites-available/roadready /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo systemctl restart nginx

# 6. Configure Systemd Service
echo "⚙️ Configuring Service..."
sudo cp deploy/roadready.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable roadready
sudo systemctl restart roadready

echo "✅ Deployment Complete!"
echo "🌍 Your app should be live on your VM's public IP."
