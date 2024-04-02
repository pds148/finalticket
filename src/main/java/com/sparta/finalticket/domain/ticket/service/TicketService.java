package com.sparta.finalticket.domain.ticket.service;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sparta.finalticket.domain.game.entity.Game;
import com.sparta.finalticket.domain.game.repository.GameRepository;
import com.sparta.finalticket.domain.seat.entity.Seat;
import com.sparta.finalticket.domain.seat.repository.SeatRepository;
import com.sparta.finalticket.domain.seatsetting.entity.Seatsetting;
import com.sparta.finalticket.domain.seatsetting.repository.SeatSettingRepository;
import com.sparta.finalticket.domain.ticket.entity.QTicket;
import com.sparta.finalticket.domain.ticket.entity.Ticket;
import com.sparta.finalticket.domain.ticket.repository.TicketRepository;
import com.sparta.finalticket.domain.user.entity.User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final GameRepository gameRepository;

    private final SeatRepository seatRepository;

    private final SeatSettingRepository seatsettingRepository;

    private final TicketRepository ticketRepository;

    private final JPAQueryFactory jpaQueryFactory;

    public List<Ticket> getUserTicketList(User user) {
        QTicket ticket = QTicket.ticket;
        return jpaQueryFactory.selectFrom(ticket).where(ticket.user.id.eq(user.getId())).fetchAll().stream().toList();
    }

    //티켓팅
    @Transactional
    public void createTicket(Long gameId, Long seatId, User user) {

        boolean existingTicket = seatRepository.existsByUserAndGameIdAndSeatsettingIdAndState(user, gameId, seatId, false);
        if (!existingTicket) {
            Game game = getGame(gameId);
            Seatsetting seatSetting = getSeatsetting(seatId);

            validateSeatExist(user, game, seatId, true);
            Seat seat = new Seat(game, seatSetting, user, true);
            seatRepository.save(seat);

            Ticket ticket = new Ticket(user, game, seat, true);
            ticketRepository.save(ticket);
        } else {
            Seat seat = getSeat(gameId, seatId, user.getId(), false);
            seat.update(true);

            Ticket ticket = getTicket(seat.getId());
            ticket.update(true);
        }
    }

    //티켓팅 취소
    @Transactional
    public void deleteTicket(Long gameId, Long seatId, User user) {
        Seat seat = getSeat(gameId, seatId, user.getId(), true);
        seat.update(false);

        Ticket ticket = getTicket(seat.getId());
        ticket.update(false);
    }

    private Game getGame(Long gameId) {
        return gameRepository.findById(gameId).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게임입니다."));
    }

    private Seat getSeat(Long gameId, Long seatId, Long userId, boolean b) {
        return seatRepository.findSeatByGameIdAndSeatsettingIdAndUserIdAndState(gameId, seatId, userId, b)
                .orElseThrow(() -> new IllegalArgumentException("예약되지 않은 좌석 입니다."));
    }

    private Seatsetting getSeatsetting(Long seatId) {
        return seatsettingRepository.findById(seatId).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 좌석입니다"));
    }

    private Ticket getTicket(Long gameId) {
        return ticketRepository.findBySeatId(gameId)
                .orElseThrow(() -> new IllegalArgumentException("예약되지 않은 티켓 입니다."));
    }

    private void validateSeatExist(User user, Game game, Long seatId, Boolean b) {
        if (seatRepository.existsByUserAndGameAndSeatsettingIdAndState(user, game, seatId, b)) {
            throw new IllegalArgumentException("이미 예매된 좌석입니다.");
        }
    }
}
