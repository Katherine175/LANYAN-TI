import { articles, problems, requests, services, tickets, users } from '../data/mockData'
import type { KnowledgeArticle, Problem, Service, Ticket, User, WorkflowRequest } from '../types'

const wait = (ms = 180) => new Promise((resolve) => setTimeout(resolve, ms))

export const itsmService = {
  async getTickets(): Promise<Ticket[]> {
    await wait()
    return structuredClone(tickets)
  },
  async createTicket(ticket: Omit<Ticket, 'id' | 'createdAt' | 'comments' | 'sla'>): Promise<Ticket> {
    await wait()
    return { ...ticket, id: `INC-${1050 + Math.floor(Math.random() * 100)}`, createdAt: 'Ahora', comments: 0, sla: 100 }
  },
  async getServices(): Promise<Service[]> {
    await wait()
    return structuredClone(services)
  },
  async getRequests(): Promise<WorkflowRequest[]> {
    await wait()
    return structuredClone(requests)
  },
  async getArticles(): Promise<KnowledgeArticle[]> {
    await wait()
    return structuredClone(articles)
  },
  async getProblems(): Promise<Problem[]> {
    await wait()
    return structuredClone(problems)
  },
  async getUsers(): Promise<User[]> {
    await wait()
    return structuredClone(users)
  },
}
