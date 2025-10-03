/**
 * User Repository
 * 사용자 데이터 접근 계층
 */

import { db, type User } from '../schema'

export class UserRepository {
  /**
   * 이메일로 사용자 조회
   */
  async findByEmail(email: string): Promise<User | undefined> {
    return await db.users.where('email').equals(email).first()
  }

  /**
   * 사용자 생성
   */
  async create(email: string, role: 'USER' | 'ADMIN' = 'USER'): Promise<number> {
    const id = await db.users.add({
      email,
      role,
      createdAt: new Date()
    })
    return id as number
  }

  /**
   * ID로 사용자 조회
   */
  async findById(id: number): Promise<User | undefined> {
    return await db.users.get(id)
  }

  /**
   * 모든 사용자 조회
   */
  async findAll(): Promise<User[]> {
    return await db.users.toArray()
  }

  /**
   * 사용자 삭제
   */
  async delete(id: number): Promise<void> {
    await db.users.delete(id)
  }
}

// Singleton export
export const userRepository = new UserRepository()
