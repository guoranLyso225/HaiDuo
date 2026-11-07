import {describe,it,expect} from 'vitest';
import {formatTime} from './time';
describe('UTC展示边界',()=>{it('缺失值不伪造时间',()=>expect(formatTime(null)).toBe('—'));it('无效值保留原内容',()=>expect(formatTime('invalid-date')).toBe('invalid-date'));it('合法UTC按本机时区展示',()=>expect(formatTime('2026-01-01T00:00:00Z')).not.toContain('Invalid'));});

it('epoch milliseconds and UTC ISO denote the same instant',()=>expect(formatTime(1767225600000)).toBe(formatTime('2026-01-01T00:00:00Z')));
