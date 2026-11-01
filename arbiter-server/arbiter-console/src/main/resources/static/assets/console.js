(() => {
    'use strict';

    const TOKEN_KEY = 'arbiter.console.token';
    const USER_KEY = 'arbiter.console.user';
    let configPromise = null;

    async function config() {
        if (!configPromise) {
            configPromise = fetch('/console/config', {
                headers: {Accept: 'application/json'}
            }).then(response => {
                if (!response.ok) {
                    throw new Error('无法加载控制台配置');
                }
                return response.json();
            });
        }
        return configPromise;
    }

    async function apiPath(path) {
        const settings = await config();
        const prefix = settings.apiPrefix === '/'
            ? ''
            : settings.apiPrefix.replace(/\/+$/, '');
        return prefix + path;
    }

    function token() {
        return sessionStorage.getItem(TOKEN_KEY);
    }

    function currentUser() {
        const value = sessionStorage.getItem(USER_KEY);
        if (!value) {
            return null;
        }
        try {
            return JSON.parse(value);
        } catch (failure) {
            clearSession();
            return null;
        }
    }

    function isAdmin() {
        const user = currentUser();
        return user !== null && user.scope === 'admin';
    }

    function storeSession(login) {
        sessionStorage.setItem(TOKEN_KEY, login.token);
        sessionStorage.setItem(USER_KEY, JSON.stringify({
            username: login.username,
            scope: login.scope,
            expiresAt: login.expiresAt
        }));
    }

    function clearSession() {
        sessionStorage.removeItem(TOKEN_KEY);
        sessionStorage.removeItem(USER_KEY);
    }

    async function request(method, path, body, authenticated = true) {
        const url = await apiPath(path);
        const headers = {Accept: 'application/json'};
        if (body !== undefined) {
            headers['Content-Type'] = 'application/json';
        }
        if (authenticated && token()) {
            headers.ARBITER_TOKEN = token();
        }
        const response = await fetch(url, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body)
        });
        if (response.status === 401 && authenticated) {
            clearSession();
            location.replace('/login.html');
            throw new Error('登录已过期，请重新登录');
        }
        const text = await response.text();
        let data = null;
        if (text) {
            try {
                data = JSON.parse(text);
            } catch (failure) {
                data = null;
            }
        }
        if (!response.ok) {
            const message = data && data.message ? data.message : '请求失败';
            throw new Error(message);
        }
        return data;
    }

    async function login(username, password) {
        const result = await request('POST', '/auth/login', {username, password}, false);
        storeSession(result);
        return result;
    }

    function logout() {
        clearSession();
        location.replace('/login.html');
    }

    function requireAuth(adminOnly = false) {
        const user = currentUser();
        if (!user || !token()) {
            location.replace('/login.html');
            throw new Error('redirecting');
        }
        if (adminOnly && user.scope !== 'admin') {
            location.replace('/home.html');
            throw new Error('redirecting');
        }
        return user;
    }

    function initShell(active, adminOnly = false) {
        const user = requireAuth(adminOnly);
        document.querySelectorAll('[data-user-name]').forEach(element => {
            element.textContent = user.username;
        });
        document.querySelectorAll('[data-user-scope]').forEach(element => {
            element.textContent = user.scope;
        });
        if (user.scope !== 'admin') {
            document.querySelectorAll('[data-admin-only]').forEach(element => {
                element.remove();
            });
        }
        document.querySelectorAll('[data-nav]').forEach(element => {
            if (element.dataset.nav === active) {
                element.classList.add('active');
            }
        });
        document.querySelectorAll('[data-logout]').forEach(element => {
            element.addEventListener('click', logout);
        });
        return user;
    }

    function showError(element, error) {
        if (!element) {
            return;
        }
        element.textContent = error && error.message ? error.message : String(error);
        element.hidden = false;
    }

    window.ArbiterConsole = {
        api: request,
        clearSession,
        config,
        currentUser,
        initShell,
        isAdmin,
        login,
        logout,
        requireAuth,
        showError,
        token
    };
})();
