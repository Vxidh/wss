// No JWT required for dashboard
function fetchNodes() {
    fetch('/api/nodes')
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

// Poll nodes every 5 seconds
fetchNodes();
setInterval(fetchNodes, 5000);
