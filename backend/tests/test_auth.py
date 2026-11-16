"""
Integration tests for CampusNav Authentication & User Management.
"""
import pytest
import json
from backend.app.models.user import User


def test_register_success(client, db):
    payload = {
        'email': 'student@thapar.edu',
        'password': 'SecurePassword123!',
        'first_name': 'Rohit',
        'last_name': 'Sharma',
        'role': 'student',
        'student_id': '102103999'
    }
    res = client.post('/api/auth/register', json=payload)
    assert res.status_code == 201
    data = res.get_json()
    assert data['success'] is True
    assert data['data']['email'] == 'student@thapar.edu'
    assert 'password_hash' not in data['data']


def test_register_duplicate_email(client, db):
    payload = {
        'email': 'duplicate@thapar.edu',
        'password': 'SecurePassword123!',
        'first_name': 'Aman',
        'last_name': 'Singh'
    }
    res1 = client.post('/api/auth/register', json=payload)
    assert res1.status_code == 201

    res2 = client.post('/api/auth/register', json=payload)
    assert res2.status_code == 400
    data = res2.get_json()
    assert data['success'] is False


def test_register_weak_password(client, db):
    payload = {
        'email': 'weak@thapar.edu',
        'password': '123',
        'first_name': 'Test',
        'last_name': 'User'
    }
    res = client.post('/api/auth/register', json=payload)
    assert res.status_code == 400


def test_login_and_logout(client, db):
    # Register user first
    client.post('/api/auth/register', json={
        'email': 'login_test@thapar.edu',
        'password': 'LoginPassword123!',
        'first_name': 'Test',
        'last_name': 'Login'
    })

    # Test invalid credentials
    res_bad = client.post('/api/auth/login', json={
        'email': 'login_test@thapar.edu',
        'password': 'WrongPassword!'
    })
    assert res_bad.status_code == 401

    # Test valid credentials
    res_good = client.post('/api/auth/login', json={
        'email': 'login_test@thapar.edu',
        'password': 'LoginPassword123!'
    })
    assert res_good.status_code == 200
    data = res_good.get_json()
    assert data['success'] is True
    assert data['data']['email'] == 'login_test@thapar.edu'

    # Test me endpoint
    res_me = client.get('/api/auth/me')
    assert res_me.status_code == 200

    # Logout
    res_logout = client.post('/api/auth/logout')
    assert res_logout.status_code == 200
