// Replace this with a secure way to get a JWT for the dashboard in production
const DASHBOARD_NODE_ID = "dashboard-ui";
let jwtToken = null;

function fetchJwtToken() {
    return fetch(`/api/generate-jwt/${DASHBOARD_NODE_ID}`)
        .then(resp => resp.json())
        .then(data => data.token);
}

function fetchNodes() {
    if (!jwtToken) {
        // Wait for token before making API call
        return;
    }
    fetch('/api/nodes', {
        headers: { 'Authorization': 'Bearer ' + jwtToken }
    })
        .then(resp => {
            if (!resp.ok) throw new Error('Failed to fetch nodes');
            return resp.json();
        })
        .then(nodes => {
            const table = document.getElementById('nodesTable');
            // Remove old rows except header
            while (table.rows.length > 1) table.deleteRow(1);
            nodes.forEach(node => {
                const row = table.insertRow();
                row.className = node.status;
                row.insertCell().textContent = node.id;
                row.insertCell().textContent = node.status;
                row.insertCell().textContent = new Date(node.lastActivity).toLocaleString();
            });
        })
        .catch(err => {
            // Optionally display error to user
            console.error('Error loading nodes:', err);
        });
}

// Get JWT first, then start polling
fetchJwtToken().then(token => {
    jwtToken = token;
    fetchNodes();
    setInterval(fetchNodes, 5000);
}).catch(err => {
    console.error('Failed to get JWT token for dashboard:', err);
});
