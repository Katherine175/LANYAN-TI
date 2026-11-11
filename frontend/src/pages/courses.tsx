import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { BookOpen, CalendarDays, ChevronRight, Clock3, GraduationCap, Languages, MapPin, Plus, Search, Users } from 'lucide-react'
import { Badge, Button, Card, Input, Modal, PageHeader, Select, Textarea } from '../components/ui'
import {
  courseStatusLabels, createCourse, createSchedule, createSection, formatDate, getCourseCatalog, listCourses, listSchedules, listSections,
  modalityLabels, sectionStatusLabels, shortTime, weekdayLabels,
  type CatalogCourse, type CatalogSection, type Course, type CourseStatus, type Modality, type Schedule, type Section, type SectionStatus, type Weekday,
} from '../services/academicService'

const tones = ['lavender', 'blue', 'mint', 'pink']
const errorText = (error: unknown) => (error instanceof Error ? error.message : 'No se pudo completar la operación.')
const seatsText = (seats: number) => (seats === 0 ? 'Sin cupos' : seats === 1 ? '1 cupo libre' : `${seats} cupos libres`)
const openSeats = (course: CatalogCourse) => course.sections.reduce((total, section) => total + section.availableSeats, 0)

export function CourseCatalog({ isAdmin }: { isAdmin: boolean }) {
  const [courses, setCourses] = useState<CatalogCourse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [language, setLanguage] = useState('Todos')
  const [selected, setSelected] = useState<CatalogCourse | null>(null)

  useEffect(() => {
    getCourseCatalog().then(setCourses).catch((e) => setError(errorText(e))).finally(() => setLoading(false))
  }, [])

  const languages = ['Todos', ...new Set(courses.map((course) => course.language))]
  const visible = useMemo(() => {
    const text = query.trim().toLowerCase()
    return courses.filter((course) => (language === 'Todos' || course.language === language)
      && `${course.name} ${course.code} ${course.language} ${course.level ?? ''} ${course.description ?? ''}`.toLowerCase().includes(text))
  }, [courses, query, language])

  return (
    <>
      <PageHeader eyebrow="Instituto de Idiomas" title="Catálogo de cursos" description={isAdmin ? 'Así ven los estudiantes la oferta vigente: cursos activos y secciones abiertas.' : 'Explora los idiomas que ofrece el Instituto, sus horarios y los cupos disponibles.'} />
      <div className="catalog-hero"><div><Languages size={21} /><h2>¿Qué idioma quieres aprender?</h2><p>Busca por idioma, nivel o nombre del curso.</p></div><div className="hero-search"><Search size={19} /><Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Ej. Inglés intermedio" /></div></div>
      {languages.length > 2 && <div className="filter-pills">{languages.map((item) => <Button key={item} variant={language === item ? 'primary' : 'secondary'} onClick={() => setLanguage(item)}>{item}</Button>)}</div>}
      {error && <div className="error-note">{error}</div>}
      <div className="service-grid course-grid">
        {visible.map((course, index) => {
          const seats = openSeats(course)
          return (
            <Card className="service-card course-card" key={course.id} onClick={() => setSelected(course)}>
              <div className={`service-icon icon-${tones[index % 4]}`}><GraduationCap size={24} /></div>
              {course.level && <Badge>{course.level}</Badge>}
              <span className="course-language">{course.language}</span>
              <h3>{course.name}</h3>
              <p>{course.description ?? 'Sin descripción por ahora.'}</p>
              <div className="service-meta">
                <span><CalendarDays size={14} />{course.sections.length === 0 ? 'Sin secciones abiertas' : course.sections.length === 1 ? '1 sección abierta' : `${course.sections.length} secciones abiertas`}</span>
                {course.sections.length > 0 && <span><Users size={14} />{seatsText(seats)}</span>}
              </div>
              <Button variant="ghost">Ver horarios <ChevronRight size={16} /></Button>
            </Card>
          )
        })}
      </div>
      {!loading && !error && courses.length === 0 && <div className="empty-state"><BookOpen size={24} /><strong>Aún no hay cursos publicados</strong><span>{isAdmin ? 'Crea un curso activo con una sección abierta en Administración › Cursos.' : 'Vuelve pronto: aquí aparecerá la oferta del Instituto.'}</span></div>}
      {!loading && courses.length > 0 && visible.length === 0 && <div className="empty-state"><Search size={24} /><strong>Sin resultados</strong><span>Prueba con otro idioma o palabra.</span></div>}
      {selected && <CourseDetail course={selected} onClose={() => setSelected(null)} />}
    </>
  )
}

function CourseDetail({ course, onClose }: { course: CatalogCourse; onClose: () => void }) {
  return (
    <Modal open onClose={onClose} title={course.name} footer={<Button variant="secondary" onClick={onClose}>Cerrar</Button>}>
      <div className="detail-callout"><div className="service-icon icon-lavender"><GraduationCap size={24} /></div><div><strong>{course.language}{course.level ? ` · Nivel ${course.level}` : ''}</strong><p>Código {course.code}</p></div></div>
      {course.description && <p className="course-description">{course.description}</p>}
      <h3 className="course-subtitle">Secciones abiertas</h3>
      {course.sections.length === 0 && <p className="muted">Este curso todavía no tiene secciones abiertas.</p>}
      {course.sections.map((section) => <SectionSummary key={section.id} section={section} />)}
    </Modal>
  )
}

function SectionSummary({ section }: { section: CatalogSection }) {
  return (
    <div className="section-box">
      <div className="section-box-head">
        <div><strong>Sección {section.code}</strong><span>{section.academicPeriod} · {formatDate(section.startDate)} al {formatDate(section.endDate)}</span></div>
        <Badge tone={section.availableSeats === 0 ? 'danger' : 'mint'}>{seatsText(section.availableSeats)}</Badge>
      </div>
      {section.schedules.length === 0
        ? <p className="muted">Horario por confirmar.</p>
        : <ul className="schedule-list">{section.schedules.map((schedule, index) => (
          <li key={index}>
            <Clock3 size={14} /><strong>{weekdayLabels[schedule.dayOfWeek]}</strong>
            <span>{shortTime(schedule.startTime)} a {shortTime(schedule.endTime)}</span>
            <span className="schedule-place"><MapPin size={13} />{modalityLabels[schedule.modality]}{schedule.classroom ? ` · ${schedule.classroom}` : ''}</span>
          </li>
        ))}</ul>}
    </div>
  )
}

/* ---------- Administración › Cursos ---------- */

export function CoursesPanel({ creating, onCloseCreate, notify }: { creating: boolean; onCloseCreate: () => void; notify: (message: string) => void }) {
  const [courses, setCourses] = useState<Course[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [opened, setOpened] = useState<Course | null>(null)

  const load = () => listCourses().then((page) => { setCourses(page.content); setError('') }).catch((e) => setError(errorText(e))).finally(() => setLoading(false))
  useEffect(() => { void load() }, [])

  return (
    <>
      <Card className="table-card">
        <div className="card-title"><div><h2>Cursos del Instituto</h2><p>Un curso aparece en el catálogo cuando está activo y tiene al menos una sección abierta.</p></div></div>
        {error && <div className="error-note">{error}</div>}
        <div className="table-scroll">
          <table>
            <thead><tr><th>Curso</th><th>Idioma</th><th>Nivel</th><th>Estado</th><th /></tr></thead>
            <tbody>
              {courses.map((course) => (
                <tr key={course.id} className="clickable" onClick={() => setOpened(course)}>
                  <td><strong>{course.name}</strong><span>{course.code}</span></td>
                  <td>{course.language}</td>
                  <td>{course.level ?? '—'}</td>
                  <td><Badge tone={course.status === 'ACTIVE' ? 'mint' : 'neutral'}>{courseStatusLabels[course.status]}</Badge></td>
                  <td><Button variant="ghost">Secciones <ChevronRight size={16} /></Button></td>
                </tr>
              ))}
            </tbody>
          </table>
          {!loading && courses.length === 0 && <div className="empty-state"><BookOpen size={24} /><strong>Todavía no hay cursos</strong><span>Usa “Añadir curso” para registrar el primero.</span></div>}
        </div>
      </Card>
      {creating && <CourseForm onClose={onCloseCreate} onCreated={(course) => { notify(`Curso ${course.code} creado`); void load(); setOpened(course) }} />}
      {opened && <SectionsModal course={opened} onClose={() => setOpened(null)} notify={notify} />}
    </>
  )
}

function CourseForm({ onClose, onCreated }: { onClose: () => void; onCreated: (course: Course) => void }) {
  const [form, setForm] = useState({ code: '', name: '', language: '', level: '', description: '', status: 'ACTIVE' as CourseStatus })
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      const course = await createCourse({ ...form, level: form.level || undefined, description: form.description || undefined })
      onCreated(course)
      onClose()
    } catch (e) {
      setError(errorText(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open onClose={onClose} title="Añadir curso" footer={<><Button variant="secondary" onClick={onClose}>Cancelar</Button><Button type="submit" form="course-form" disabled={saving}>{saving ? 'Guardando…' : 'Crear curso'}</Button></>}>
      <form id="course-form" className="account-form" onSubmit={submit}>
        <div className="form-grid">
          <label className="field-label">Nombre<Input required maxLength={160} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Ej. Inglés intermedio" /></label>
          <label className="field-label">Código<Input required maxLength={32} value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} placeholder="Ej. ING-B1" /></label>
        </div>
        <div className="form-grid">
          <label className="field-label">Idioma<Input required maxLength={80} value={form.language} onChange={(e) => setForm({ ...form, language: e.target.value })} placeholder="Ej. Inglés" /></label>
          <label className="field-label">Nivel<Input maxLength={80} value={form.level} onChange={(e) => setForm({ ...form, level: e.target.value })} placeholder="Opcional, ej. B1" /></label>
        </div>
        <label className="field-label">Descripción<Textarea rows={3} maxLength={2000} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="Qué aprenderá el estudiante (opcional)" /></label>
        <label className="field-label">Estado<Select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value as CourseStatus })}><option value="ACTIVE">Activo: visible en el catálogo</option><option value="INACTIVE">Inactivo: oculto</option></Select></label>
        {error && <div className="error-note">{error}</div>}
      </form>
    </Modal>
  )
}

function SectionsModal({ course, onClose, notify }: { course: Course; onClose: () => void; notify: (message: string) => void }) {
  const [sections, setSections] = useState<Section[]>([])
  const [schedules, setSchedules] = useState<Record<string, Schedule[]>>({})
  const [adding, setAdding] = useState(false)
  const [scheduleFor, setScheduleFor] = useState<string | null>(null)
  const [error, setError] = useState('')

  const load = async () => {
    try {
      const page = await listSections(course.id)
      setSections(page.content)
      const lists = await Promise.all(page.content.map((section) => listSchedules(section.id)))
      setSchedules(Object.fromEntries(page.content.map((section, index) => [section.id, lists[index].content])))
      setError('')
    } catch (e) {
      setError(errorText(e))
    }
  }
  useEffect(() => { void load() }, [course.id])

  return (
    <Modal open onClose={onClose} title={`${course.name} · ${course.code}`} footer={<><Button variant="secondary" onClick={onClose}>Cerrar</Button>{!adding && <Button onClick={() => setAdding(true)}><Plus size={16} />Añadir sección</Button>}</>}>
      {course.status === 'INACTIVE' && <div className="error-note">Este curso está inactivo: sus secciones no aparecen en el catálogo.</div>}
      {sections.length === 0 && !adding && <p className="muted">Este curso aún no tiene secciones. Añade una para que los estudiantes vean horarios y cupos.</p>}
      {sections.map((section) => (
        <div className="section-box" key={section.id}>
          <div className="section-box-head">
            <div><strong>Sección {section.code}</strong><span>{section.academicPeriod} · {formatDate(section.startDate)} al {formatDate(section.endDate)} · {section.capacity} cupos</span></div>
            <Badge tone={section.status === 'OPEN' ? 'mint' : section.status === 'CANCELLED' ? 'danger' : 'neutral'}>{sectionStatusLabels[section.status]}</Badge>
          </div>
          {(schedules[section.id] ?? []).length > 0 && <ul className="schedule-list">{(schedules[section.id] ?? []).map((schedule) => (
            <li key={schedule.id}><Clock3 size={14} /><strong>{weekdayLabels[schedule.dayOfWeek]}</strong><span>{shortTime(schedule.startTime)} a {shortTime(schedule.endTime)}</span><span className="schedule-place"><MapPin size={13} />{modalityLabels[schedule.modality]}{schedule.classroom ? ` · ${schedule.classroom}` : ''}</span></li>
          ))}</ul>}
          {scheduleFor === section.id
            ? <ScheduleForm sectionId={section.id} onCancel={() => setScheduleFor(null)} onSaved={() => { setScheduleFor(null); notify('Horario agregado'); void load() }} />
            : <Button variant="ghost" onClick={() => setScheduleFor(section.id)}><Plus size={15} />Añadir horario</Button>}
        </div>
      ))}
      {adding && <SectionForm courseId={course.id} onCancel={() => setAdding(false)} onSaved={() => { setAdding(false); notify('Sección creada'); void load() }} />}
      {error && <div className="error-note">{error}</div>}
    </Modal>
  )
}

function SectionForm({ courseId, onCancel, onSaved }: { courseId: string; onCancel: () => void; onSaved: () => void }) {
  const [form, setForm] = useState({ code: '', academicPeriod: '', startDate: '', endDate: '', capacity: '25', status: 'OPEN' as SectionStatus })
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (form.endDate < form.startDate) { setError('La fecha de fin no puede ser anterior al inicio.'); return }
    setSaving(true)
    setError('')
    try {
      await createSection(courseId, { ...form, capacity: Number(form.capacity) })
      onSaved()
    } catch (e) {
      setError(errorText(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="inline-form" onSubmit={submit}>
      <strong>Nueva sección</strong>
      <div className="form-grid">
        <label className="field-label">Sección<Input required maxLength={32} value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} placeholder="Ej. A" /></label>
        <label className="field-label">Periodo<Input required maxLength={40} value={form.academicPeriod} onChange={(e) => setForm({ ...form, academicPeriod: e.target.value })} placeholder="Ej. 2026-I" /></label>
      </div>
      <div className="form-grid">
        <label className="field-label">Inicio<Input required type="date" value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })} /></label>
        <label className="field-label">Fin<Input required type="date" value={form.endDate} onChange={(e) => setForm({ ...form, endDate: e.target.value })} /></label>
      </div>
      <div className="form-grid">
        <label className="field-label">Cupos<Input required type="number" min={1} value={form.capacity} onChange={(e) => setForm({ ...form, capacity: e.target.value })} /></label>
        <label className="field-label">Estado<Select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value as SectionStatus })}>{(Object.keys(sectionStatusLabels) as SectionStatus[]).map((status) => <option key={status} value={status}>{sectionStatusLabels[status]}</option>)}</Select></label>
      </div>
      {error && <div className="error-note">{error}</div>}
      <div className="row-actions"><Button type="button" variant="secondary" onClick={onCancel}>Cancelar</Button><Button type="submit" disabled={saving}>{saving ? 'Guardando…' : 'Guardar sección'}</Button></div>
    </form>
  )
}

function ScheduleForm({ sectionId, onCancel, onSaved }: { sectionId: string; onCancel: () => void; onSaved: () => void }) {
  const [form, setForm] = useState({ dayOfWeek: 'MONDAY' as Weekday, startTime: '18:00', endTime: '20:00', modality: 'IN_PERSON' as Modality, classroom: '' })
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (form.endTime <= form.startTime) { setError('La hora de fin debe ser posterior al inicio.'); return }
    if (form.modality !== 'ONLINE' && !form.classroom.trim()) { setError('Indica el aula para clases presenciales o híbridas.'); return }
    setSaving(true)
    setError('')
    try {
      await createSchedule(sectionId, { ...form, classroom: form.classroom.trim() || undefined })
      onSaved()
    } catch (e) {
      setError(errorText(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="inline-form" onSubmit={submit}>
      <div className="form-grid">
        <label className="field-label">Día<Select value={form.dayOfWeek} onChange={(e) => setForm({ ...form, dayOfWeek: e.target.value as Weekday })}>{(Object.keys(weekdayLabels) as Weekday[]).map((day) => <option key={day} value={day}>{weekdayLabels[day]}</option>)}</Select></label>
        <label className="field-label">Modalidad<Select value={form.modality} onChange={(e) => setForm({ ...form, modality: e.target.value as Modality })}>{(Object.keys(modalityLabels) as Modality[]).map((item) => <option key={item} value={item}>{modalityLabels[item]}</option>)}</Select></label>
      </div>
      <div className="form-grid">
        <label className="field-label">Desde<Input required type="time" value={form.startTime} onChange={(e) => setForm({ ...form, startTime: e.target.value })} /></label>
        <label className="field-label">Hasta<Input required type="time" value={form.endTime} onChange={(e) => setForm({ ...form, endTime: e.target.value })} /></label>
      </div>
      {form.modality !== 'ONLINE' && <label className="field-label">Aula<Input maxLength={120} value={form.classroom} onChange={(e) => setForm({ ...form, classroom: e.target.value })} placeholder="Ej. Aula 101" /></label>}
      {error && <div className="error-note">{error}</div>}
      <div className="row-actions"><Button type="button" variant="secondary" onClick={onCancel}>Cancelar</Button><Button type="submit" disabled={saving}>{saving ? 'Guardando…' : 'Guardar horario'}</Button></div>
    </form>
  )
}
