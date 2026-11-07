import RolePermissions from './RolePermissions';
import Warehouses from './Warehouses';
import {Tabs} from 'antd';
import AuditLog from './AuditLog';
import Members from './Members';
export default function Settings(){return <Tabs items={[{key:'audit',label:'审计日志',children:<AuditLog/>},{key:'members',label:'企业成员',children:<Members/>},{key:'warehouses',label:'仓库管理',children:<Warehouses/>},{key:'roles',label:'角色权限',children:<RolePermissions/>}]}/>}
