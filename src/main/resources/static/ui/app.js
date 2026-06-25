const API_BASE = window.location.origin;

function api(path, opts = {}) {
  return fetch(API_BASE + path, { headers: { 'Content-Type': 'application/json' }, ...opts })
    .then(r => r.ok ? r.json() : r.text().then(t => { throw new Error(t) }));
}

// === Dashboard ===
const DashboardPage = {
  template: `
    <div>
      <div class="stat-cards">
        <el-card><h3>Status</h3><p :style="{color: health.status === 'UP' ? '#67c23a' : '#f56c6c', fontSize: '24px', fontWeight: 'bold'}">{{ health.status }}</p></el-card>
        <el-card><h3>Roles</h3><p style="font-size:24px;font-weight:bold">{{ health.roles?.length || 0 }}</p></el-card>
        <el-card><h3>Version</h3><p>{{ health.version }}</p></el-card>
        <el-card><h3>Policy Source</h3><p style="font-size:12px">{{ health.policySource }}</p></el-card>
      </div>
      <el-card><template #header><span>Roles</span></template>
        <el-table :data="roleList" stripe style="width:100%">
          <el-table-column prop="id" label="ID" width="180"/>
          <el-table-column prop="workDir" label="WorkDir"/>
          <el-table-column label="Commands" width="200">
            <template #default="{row}">{{ row.allowedCommands?.map(c => c.command).join(', ') || '-' }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>`,
  data: () => ({ health: {}, roleList: [] }),
  created() {
    this.load();
  },
  methods: {
    async load() {
      try {
        this.health = await api('/api/v1/guard/health');
      } catch (e) {
        this.health = { status: 'DOWN' };
      }
      try {
        this.roleList = await api('/api/v1/policy/roles');
      } catch (e) {
        this.roleList = [];
      }
    }
  }
};

// === Roles ===
const RolesPage = {
  template: `
    <div>
      <div style="margin-bottom:16px">
        <el-button type="primary" @click="showNew = true">+ New Role</el-button>
        <el-button @click="reload">Refresh</el-button>
      </div>
      <el-table :data="roles" stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="160"/>
        <el-table-column prop="workDir" label="WorkDir"/>
        <el-table-column label="Commands" width="240">
          <template #default="{row}">{{ (row.allowedCommands || []).map(c => c.command + '(' + (c.subcommands || []).join(',') + ')').join('; ') }}</template>
        </el-table-column>
        <el-table-column label="Actions" width="160">
          <template #default="{row}">
            <el-button size="small" @click="editRole(row)">Edit</el-button>
            <el-button size="small" type="danger" @click="deleteRole(row.id)">Delete</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-dialog v-model="showNew" title="New Role" width="500px" class="role-dialog">
        <el-form :model="form" label-width="100px">
          <el-form-item label="ID"><el-input v-model="form.id" placeholder="MY_ROLE"/></el-form-item>
          <el-form-item label="WorkDir"><el-input v-model="form.workDir" placeholder="C:/project"/></el-form-item>
          <el-form-item label="Commands"><el-input v-model="form.commandsRaw" type="textarea" :rows="3" placeholder="git:status,add,commit,push|npm:install,run"/></el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showNew = false">Cancel</el-button>
          <el-button type="primary" @click="saveRole">Save</el-button>
        </template>
      </el-dialog>
    </div>`,
  data: () => ({ roles: [], showNew: false, form: { id: '', workDir: '', commandsRaw: '' } }),
  created() { this.load(); },
  methods: {
    async load() { this.roles = await api('/api/v1/policy/roles'); },
    editRole(row) { this.form = { id: row.id, workDir: row.workDir, commandsRaw: (row.allowedCommands || []).map(c => c.command + ':' + (c.subcommands || []).join(',')).join('|') }; this.showNew = true; },
    async deleteRole(id) { await api('/api/v1/policy/roles/' + id, { method: 'DELETE' }); this.load(); },
    async saveRole() {
      const commands = this.form.commandsRaw.split('|').filter(Boolean).map(s => {
        const [cmd, subs] = s.split(':');
        return { command: cmd, subcommands: subs ? subs.split(',').filter(Boolean) : [] };
      });
      await api('/api/v1/policy/roles', { method: 'PUT', body: JSON.stringify({ id: this.form.id, workDir: this.form.workDir, allowedCommands: commands }) });
      this.showNew = false; this.form = { id: '', workDir: '', commandsRaw: '' }; this.load();
    },
    reload() { this.load(); }
  }
};

// === Audit ===
const AuditPage = {
  template: `
    <div>
      <div style="margin-bottom:16px;display:flex;gap:12px">
        <el-input v-model="filterRole" placeholder="Filter by role" style="width:200px" clearable/>
        <el-input v-model="filterOp" placeholder="Filter by operation" style="width:200px" clearable/>
        <el-input-number v-model="limit" :min="5" :max="100" :step="5" style="width:120px"/>
        <el-button type="primary" @click="load">Search</el-button>
        <el-button @click="clearFilter">Clear</el-button>
      </div>
      <el-card>
        <div v-if="entries === null || entries.length === 0" style="text-align:center;color:#999;padding:40px">No audit entries found. Try making some API calls first.</div>
        <div v-for="(e, i) in entries" :key="i" class="audit-entry">{{ e }}</div>
        <div v-if="entries.length > 0" style="text-align:center;margin-top:12px">
          <el-pagination background layout="prev,pager,next" :total="total" :page-size="limit" @current-change="page => { offset = (page-1)*limit; load(); }"/>
        </div>
      </el-card>
    </div>`,
  data: () => ({ entries: null, filterRole: '', filterOp: '', limit: 20, offset: 0, total: 0 }),
  created() { this.load(); },
  methods: {
    async load() {
      try {
        const params = new URLSearchParams();
        if (this.filterRole) params.set('role', this.filterRole);
        if (this.filterOp) params.set('operation', this.filterOp);
        params.set('limit', this.limit); params.set('offset', this.offset);
        const r = await api('/api/v1/guard/audit?' + params.toString());
        this.entries = r.entries || [];
        this.total = r.totalReturned || 0;
      } catch (e) { this.entries = []; }
    },
    clearFilter() { this.filterRole = ''; this.filterOp = ''; this.offset = 0; this.load(); }
  }
};

// === App ===
const app = Vue.createApp({
  data: () => ({
    currentRoute: null, // set in created()
    healthStatus: 'Checking...',
    health: {},
    pageTitles: { '/dashboard': 'Dashboard', '/roles': 'Role Management', '/audit': 'Audit Log' }
  }),
  computed: { pageTitle() { return this.pageTitles[this.currentRoute] || 'DevPilot Sandbox'; } },
  created() { this.loadHealth(); this.currentRoute = window.location.hash?.slice(1) || '/dashboard'; },
  methods: {
    navigate(idx) { this.currentRoute = idx; window.location.hash = '#' + idx; },
    async loadHealth() {
      try { const h = await api('/api/v1/guard/health'); this.healthStatus = h.status; this.health = h; }
      catch (e) { this.healthStatus = 'DOWN'; }
    }
  }
});

app.component('Monitor', { template: '<svg ...>...</svg>' });
app.component('Setting', { template: '<svg ...>...</svg>' });
app.component('Document', { template: '<svg ...>...</svg>' });

app.component('DashboardPage', DashboardPage);
app.component('RolesPage', RolesPage);
app.component('AuditPage', AuditPage);

app.mount('#app');
