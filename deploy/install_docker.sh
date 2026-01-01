#!/bin/bash

# Exit on error
set -e

echo "🐳 Starting Road Ready Docker Deployment..."

# 1. Add Swap Space (CRITICAL for e2-micro)
# e2-micro has 1GB RAM. Docker builds can crash it. We add 2GB swap.
if [ ! -f /swapfile ]; then
    echo "💾 Creating 2GB Swap File..."
    sudo fallocate -l 2G /swapfile
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi

# 2. Install Docker & Nginx
echo "📦 Installing Docker and Nginx..."
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-v2 nginx

# 3. Setup Project Directory
echo "📂 Setting up Directory..."
sudo mkdir -p /opt/road-ready
# Ensure user has permissions
sudo usermod -aG docker $USER
# Copy files (Assumes you run this from inside the uploaded folder)
sudo cp -r . /opt/road-ready/

# 4. Configure Nginx (Reverse Proxy)
echo "🌐 Configuring Nginx..."
sudo cp deploy/nginx.conf /etc/nginx/sites-available/roadready
sudo ln -sf /etc/nginx/sites-available/roadready /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo systemctl restart nginx

# 5. Build and Run
echo "🚀 Building and Starting Container..."
cd /opt/road-ready
# We use sudo for docker commands here just in case user permissions aren't fresh
sudo docker compose up -d --build

echo "✅ Deployment Complete!"
echo "🌍 App is running on port 80 (via Nginx proxy to Docker port 8000)."
