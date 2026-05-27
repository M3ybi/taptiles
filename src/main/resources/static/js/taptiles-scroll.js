(function () {
    var timerId = null;

    function replaceFromResponse(html) {
        var next = new DOMParser().parseFromString(html, 'text/html');
        replaceElement('.app-header', next);
        replaceElement('.game-layout', next);
        syncThemeToggle();
        syncSideTabs();
        closeNewGameModal();
        startTimer();
        drawBoardPaths();
    }

    function replaceElement(selector, nextDocument) {
        var current = document.querySelector(selector);
        var replacement = nextDocument.querySelector(selector);
        if (current && replacement) {
            current.replaceWith(replacement);
        }
    }

    function fetchHtml(url, options) {
        return fetch(url, options).then(function (response) {
            if (!response.ok) {
                throw new Error('Request failed: ' + response.status);
            }
            return response.text();
        }).then(replaceFromResponse).catch(function () {
            window.location.href = url;
        });
    }

    function ajaxLink(link) {
        fetchHtml(link.href, {
            credentials: 'same-origin',
            headers: {'X-Requested-With': 'fetch'}
        });
    }

    function ajaxForm(form) {
        var method = (form.getAttribute('method') || 'GET').toUpperCase();
        var action = form.action;
        var options = {
            method: method,
            credentials: 'same-origin',
            headers: {'X-Requested-With': 'fetch'}
        };

        if (method === 'GET') {
            var params = new URLSearchParams(new FormData(form));
            var separator = action.indexOf('?') === -1 ? '?' : '&';
            if (params.toString()) {
                action += separator + params.toString();
            }
        } else {
            options.body = new URLSearchParams(new FormData(form));
            options.headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
        }

        fetchHtml(action, options);
    }

    document.addEventListener('click', function (event) {
        var target = event.target;
        if (!target || !target.closest) {
            return;
        }

        var themeButton = target.closest('.theme-toggle');
        if (themeButton) {
            event.preventDefault();
            toggleTheme();
            return;
        }

        var startButton = target.closest('.start-countdown');
        if (startButton) {
            event.preventDefault();
            startBoardCountdown(startButton);
            return;
        }

        var newGameButton = target.closest('.new-game-open');
        if (newGameButton) {
            event.preventDefault();
            openNewGameModal();
            return;
        }

        var entryAuthButton = target.closest('.entry-auth-open');
        if (entryAuthButton) {
            event.preventDefault();
            openEntryAuthModal();
            return;
        }

        var entryAuthClose = target.closest('[data-entry-auth-close]');
        if (entryAuthClose) {
            event.preventDefault();
            closeEntryAuthModal();
            return;
        }

        var modalClose = target.closest('[data-modal-close]');
        if (modalClose) {
            event.preventDefault();
            closeNewGameModal();
            return;
        }

        var modalChoice = target.closest('[data-play-mode]');
        if (modalChoice) {
            updateNewGamePlayMode(modalChoice.getAttribute('data-play-mode'));
            return;
        }

        var copyButton = target.closest('[data-copy-target]');
        if (copyButton) {
            event.preventDefault();
            copyInputValue(copyButton);
            return;
        }

        var sideTab = target.closest('[data-side-tab]');
        if (sideTab) {
            event.preventDefault();
            activateSideTab(sideTab.getAttribute('data-side-tab'));
            return;
        }

        var link = target.closest('.app-shell a');
        if (!link || link.classList.contains('back-link')) {
            return;
        }
        event.preventDefault();
        ajaxLink(link);
    });

    document.addEventListener('submit', function (event) {
        var form = event.target;
        if (!form || !form.closest || !form.closest('.app-shell')) {
            return;
        }
        event.preventDefault();
        ajaxForm(form);
    });

    document.addEventListener('change', function (event) {
        var input = event.target;
        if (!input || !input.closest || !input.closest('.new-game-form')) {
            return;
        }
        if (input.name === 'tutorialSize') {
            updateNewGamePlayMode('tutorial');
        } else if (input.name === 'size' || input.name === 'difficulty') {
            updateNewGamePlayMode('game');
        } else if (input.name === 'playMode') {
            updateNewGamePlayMode(input.value);
        }
    });

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
            closeNewGameModal();
            closeEntryAuthModal();
        }
    });

    function startTimer() {
        if (timerId) {
            window.clearInterval(timerId);
            timerId = null;
        }

        var timer = document.getElementById('elapsedTime');
        if (!timer) {
            return;
        }
        var elapsed = parseInt(timer.getAttribute('data-elapsed'), 10);
        var running = timer.getAttribute('data-running') === 'true';
        if (isNaN(elapsed) || !running) {
            return;
        }
        timerId = window.setInterval(function () {
            elapsed++;
            timer.textContent = elapsed + 's';
        }, 1000);
    }

    function drawHintPath() {
        var board = document.querySelector('.board-wrap');
        if (!board) {
            return;
        }

        var oldPreview = board.querySelector('.path-preview--hint');
        if (oldPreview) {
            oldPreview.remove();
        }

        var first = board.querySelector('.tile-link--hint-primary');
        var second = board.querySelector('.tile-link--hint-secondary');
        if (!first || !second) {
            return;
        }

        var points = getPathPoints(board, board.getAttribute('data-hint-path') || '');
        if (points.length < 2) {
            points = [getElementCenter(board, first), getElementCenter(board, second)];
        }

        var preview = document.createElement('div');
        preview.className = 'path-preview path-preview--hint';
        for (var index = 0; index < points.length - 1; index++) {
            appendPathSegment(preview, points[index], points[index + 1], index === points.length - 2, '', 0);
        }
        board.appendChild(preview);
    }

    function drawTutorialPaths() {
        var board = document.querySelector('.board-wrap');
        if (!board) {
            return;
        }

        var oldPreview = board.querySelector('.path-preview--tutorial');
        if (oldPreview) {
            oldPreview.remove();
        }

        if (board.getAttribute('data-tutorial-mode') !== 'true') {
            return;
        }

        var paths = (board.getAttribute('data-tutorial-paths') || '').split('|').filter(Boolean);
        if (!paths.length) {
            return;
        }

        var preview = document.createElement('div');
        preview.className = 'path-preview path-preview--tutorial';
        paths.forEach(function (path, pathIndex) {
            var points = getPathPoints(board, path);
            for (var index = 0; index < points.length - 1; index++) {
                appendPathSegment(preview, points[index], points[index + 1], index === points.length - 2, ' path-preview__line--tutorial', pathIndex * 90 + index * 120);
            }
        });
        board.appendChild(preview);
    }

    function drawBoardPaths() {
        drawHintPath();
        drawTutorialPaths();
    }

    function getPathPoints(board, path) {
        if (!path) {
            return [];
        }

        var table = board.querySelector('.field');
        var firstRow = table && table.rows.length ? table.rows[0] : null;
        var firstCell = firstRow && firstRow.cells.length ? firstRow.cells[0] : null;
        if (!firstCell) {
            return [];
        }

        var firstCenter = getElementCenter(board, firstCell);
        var secondCell = firstRow.cells.length > 1 ? firstRow.cells[1] : null;
        var secondRow = table.rows.length > 1 ? table.rows[1] : null;
        var verticalCell = secondRow && secondRow.cells.length ? secondRow.cells[0] : null;
        var pitchX = secondCell ? getElementCenter(board, secondCell).x - firstCenter.x : firstCell.getBoundingClientRect().width;
        var pitchY = verticalCell ? getElementCenter(board, verticalCell).y - firstCenter.y : firstCell.getBoundingClientRect().height;

        var playableRows = parseInt(board.getAttribute('data-board-rows'), 10) || table.rows.length;
        var playableColumns = parseInt(board.getAttribute('data-board-columns'), 10) || firstRow.cells.length;
        var firstRect = firstCell.getBoundingClientRect();
        var outsideOffset = Math.max(12, Math.min(firstRect.width, firstRect.height) * 0.18);
        var minX = firstCenter.x - pitchX / 2 - outsideOffset;
        var maxX = firstCenter.x + (playableColumns - 1) * pitchX + pitchX / 2 + outsideOffset;
        var minY = firstCenter.y - pitchY / 2 - outsideOffset;
        var maxY = firstCenter.y + (playableRows - 1) * pitchY + pitchY / 2 + outsideOffset;

        return path.split(';').map(function (point) {
            var parts = point.split(',');
            var row = parseInt(parts[0], 10);
            var column = parseInt(parts[1], 10);
            return {
                x: mapBoardCoordinate(column, playableColumns, firstCenter.x, pitchX, minX, maxX),
                y: mapBoardCoordinate(row, playableRows, firstCenter.y, pitchY, minY, maxY)
            };
        }).filter(function (point) {
            return !isNaN(point.x) && !isNaN(point.y);
        });
    }

    function mapBoardCoordinate(value, playableSize, firstCenter, pitch, minEdge, maxEdge) {
        if (value <= 0) {
            return minEdge;
        }
        if (value > playableSize) {
            return maxEdge;
        }
        return firstCenter + (value - 1) * pitch;
    }

    function getElementCenter(board, element) {
        var boardRect = board.getBoundingClientRect();
        var elementRect = element.getBoundingClientRect();
        return {
            x: elementRect.left - boardRect.left + board.scrollLeft + elementRect.width / 2,
            y: elementRect.top - boardRect.top + board.scrollTop + elementRect.height / 2
        };
    }

    function appendPathSegment(preview, first, second, isFinal, extraClass, delayMs) {
        var deltaX = second.x - first.x;
        var deltaY = second.y - first.y;
        var distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        var angle = Math.atan2(deltaY, deltaX) * 180 / Math.PI;

        var line = document.createElement('div');
        line.className = 'path-preview__line' + (isFinal ? ' path-preview__line--final' : '') + (extraClass || '');
        line.style.left = first.x + 'px';
        line.style.top = first.y + 'px';
        line.style.width = distance + 'px';
        line.style.transform = 'rotate(' + angle + 'deg)';
        line.style.setProperty('--path-angle', angle + 'deg');
        if (delayMs) {
            line.style.animationDelay = delayMs + 'ms';
        }
        preview.appendChild(line);
    }

    function startBoardCountdown(button) {
        if (button.disabled) {
            return;
        }

        var url = button.getAttribute('data-start-url');
        var display = document.querySelector('.countdown-display');
        var remaining = 3;
        button.disabled = true;
        button.textContent = 'Starting';
        if (display) {
            display.textContent = String(remaining);
        }

        var intervalId = window.setInterval(function () {
            remaining--;
            if (display) {
                display.textContent = remaining > 0 ? String(remaining) : 'Go';
            }
            if (remaining <= 0) {
                window.clearInterval(intervalId);
                fetchHtml(url, {
                    credentials: 'same-origin',
                    headers: {'X-Requested-With': 'fetch'}
                });
            }
        }, 1000);
    }

    function openNewGameModal() {
        var modal = document.querySelector('.new-game-modal');
        if (!modal) {
            return;
        }
        modal.hidden = false;
        document.body.classList.add('modal-open');
        updateNewGamePlayMode(getNewGamePlayMode());
        var closeButton = modal.querySelector('.modal-close');
        if (closeButton) {
            closeButton.focus();
        }
    }

    function closeNewGameModal() {
        var modal = document.querySelector('.new-game-modal');
        if (!modal) {
            return;
        }
        modal.hidden = true;
        document.body.classList.remove('modal-open');
    }

    function openEntryAuthModal() {
        var modal = document.querySelector('.entry-auth-modal');
        if (!modal) {
            return;
        }
        modal.hidden = false;
        var firstInput = modal.querySelector('input');
        if (firstInput) {
            firstInput.focus();
        }
    }

    function closeEntryAuthModal() {
        var modal = document.querySelector('.entry-auth-modal');
        if (!modal) {
            return;
        }
        modal.hidden = true;
    }

    function getNewGamePlayMode() {
        var input = document.querySelector('.new-game-form input[name="playMode"]:checked');
        return input ? input.value : 'game';
    }

    function updateNewGamePlayMode(playMode) {
        var input = document.querySelector('.new-game-form input[name="playMode"][value="' + (playMode === 'tutorial' ? 'tutorial' : 'game') + '"]');
        var confirm = document.querySelector('.new-game-confirm');
        var modal = document.querySelector('.new-game-modal');
        var nextMode = playMode === 'tutorial' ? 'tutorial' : 'game';
        if (input) {
            input.checked = true;
        }
        if (modal) {
            modal.classList.toggle('new-game-modal--tutorial', nextMode === 'tutorial');
            modal.classList.toggle('new-game-modal--game', nextMode === 'game');
        }
        if (confirm) {
            confirm.textContent = nextMode === 'tutorial' ? 'Start tutorial' : 'Start game';
        }
    }

    function copyInputValue(button) {
        var input = document.getElementById(button.getAttribute('data-copy-target'));
        if (!input) {
            return;
        }

        var value = input.value || '';
        var originalText = button.textContent;
        var showCopied = function () {
            button.textContent = 'Copied';
            button.classList.add('copy-seed--copied');
            window.setTimeout(function () {
                button.textContent = originalText;
                button.classList.remove('copy-seed--copied');
            }, 1200);
        };

        showCopied();

        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(value).catch(function () {
                fallbackCopy(input);
            });
            return;
        }

        fallbackCopy(input);
    }

    function fallbackCopy(input) {
        input.focus();
        input.select();
        try {
            document.execCommand('copy');
        } catch (ignored) {
        }
        input.setSelectionRange(input.value.length, input.value.length);
    }

    function getStoredTheme() {
        try {
            return window.localStorage.getItem('taptiles-theme');
        } catch (ignored) {
            return null;
        }
    }

    function storeTheme(theme) {
        try {
            window.localStorage.setItem('taptiles-theme', theme);
        } catch (ignored) {
        }
    }

    function applyTheme(theme) {
        var isDark = theme === 'dark';
        document.documentElement.classList.toggle('theme-dark', isDark);
        document.body.classList.toggle('theme-dark', isDark);
        syncThemeToggle();
    }

    function toggleTheme() {
        var nextTheme = document.body.classList.contains('theme-dark') ? 'light' : 'dark';
        applyTheme(nextTheme);
        storeTheme(nextTheme);
    }

    function syncThemeToggle() {
        var button = document.querySelector('.theme-toggle');
        if (!button) {
            return;
        }

        var isDark = document.body.classList.contains('theme-dark');
        var label = isDark ? 'Light' : 'Dark';
        button.setAttribute('aria-label', 'Switch to ' + label.toLowerCase() + ' mode');
        var text = button.querySelector('.theme-toggle__text');
        if (text) {
            text.textContent = label;
        }
    }

    function getStoredSideTab() {
        try {
            return window.sessionStorage.getItem('taptiles-side-tab') || 'challenge';
        } catch (ignored) {
            return 'challenge';
        }
    }

    function storeSideTab(tabName) {
        try {
            window.sessionStorage.setItem('taptiles-side-tab', tabName);
        } catch (ignored) {
        }
    }

    function activateSideTab(tabName) {
        var nextTab = tabName === 'leaderboard' ? 'leaderboard' : 'challenge';
        var buttons = document.querySelectorAll('[data-side-tab]');
        var panels = document.querySelectorAll('[data-side-tab-panel]');

        buttons.forEach(function (button) {
            var active = button.getAttribute('data-side-tab') === nextTab;
            button.classList.toggle('side-tab--active', active);
            button.setAttribute('aria-selected', active ? 'true' : 'false');
        });

        panels.forEach(function (panel) {
            var active = panel.getAttribute('data-side-tab-panel') === nextTab;
            panel.classList.toggle('side-tab-panel--active', active);
            panel.hidden = !active;
        });

        storeSideTab(nextTab);
    }

    function syncSideTabs() {
        if (document.querySelector('[data-side-tab]')) {
            activateSideTab(getStoredSideTab());
        }
    }

    applyTheme(getStoredTheme() || 'light');

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            syncThemeToggle();
            syncSideTabs();
            startTimer();
            drawBoardPaths();
        });
    } else {
        syncThemeToggle();
        syncSideTabs();
        startTimer();
        drawBoardPaths();
    }

    window.addEventListener('resize', drawBoardPaths);
})();
