#!/usr/bin/env python3
"""
run.py — Entry point to start the CampusNav Flask development server.

Usage:
    python backend/run.py
    python backend/run.py --env production
"""
import os
import sys
import argparse

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from backend.app import create_app

parser = argparse.ArgumentParser(description='CampusNav Flask server')
parser.add_argument('--env', default=os.environ.get('FLASK_ENV', 'development'),
                    choices=['development', 'production', 'testing'],
                    help='Configuration environment (default: development)')
parser.add_argument('--host', default='0.0.0.0', help='Host to bind (default: 0.0.0.0)')
parser.add_argument('--port', type=int, default=5000, help='Port to listen on (default: 5000)')

args = parser.parse_args()

app = create_app(args.env)

if __name__ == '__main__':
    debug = app.config.get('DEBUG', False)
    print(f'Starting CampusNav server in [{args.env}] mode on {args.host}:{args.port}')
    app.run(host=args.host, port=args.port, debug=debug)
